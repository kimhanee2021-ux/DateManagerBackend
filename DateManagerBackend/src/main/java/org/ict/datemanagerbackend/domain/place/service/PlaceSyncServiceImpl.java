package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.KopisBoxOfficeDto;
import org.ict.datemanagerbackend.domain.place.dto.KopisFacilityDto;
import org.ict.datemanagerbackend.domain.place.dto.KopisPerformanceDetailDto;
import org.ict.datemanagerbackend.domain.place.dto.KopisPerformanceDto;
import org.ict.datemanagerbackend.domain.place.entity.PerformanceRanking;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PerformanceRankingRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * KOPIS(공연예술통합전산망) 공연 목록 API에서 데이터를 받아와 places 테이블에 채워 넣는 서비스.
 *
 * 원래 파이썬 스크립트(fetch_performance_data.py)로 하던 걸 그대로 백엔드 안으로 옮긴 것.
 * 흐름: [1] KOPIS 목록에 HTTP 요청 → [2] 공연별 상세조회로 공연시설 ID(mt10id) 확보
 *      → [3] 시설 ID별로 상세조회를 한 번씩만 호출해 주소/위경도 확보(같은 시설은 캐시로 재사용)
 *      → [4] 이미 저장된 공연이면 갱신, 처음 보는 공연이면 새로 저장(upsert)
 *
 * 주의: TourApiSyncService/MuseumSyncService와 달리 PlaceDedupService(이름+좌표 중복 확인)를 안 쓴다.
 * 여기서 Place.name은 "공연 제목"(예: 레미제라블)이지 장소명이 아니라서, 같은 위치의 다른 소스
 * 장소(예: 세종문화회관)와 이름이 겹칠 일이 없고, 잘못 매칭되면 오히려 진짜 신규 공연을 놓치게 된다.
 */
@Service
@RequiredArgsConstructor // final 필드(placeRepository)를 인자로 받는 생성자를 롬복이 자동으로 만들어줌 (의존성 주입용)
@Slf4j // log.info(), log.error() 같은 로깅 기능을 롬복이 자동으로 만들어줌
public class PlaceSyncServiceImpl implements PlaceSyncService {

  // KOPIS 공연목록/상세조회(pblprfr) API의 고정 주소 - 목록은 그대로, 상세는 뒤에 /{mt20id}를 붙여서 호출
  private static final String KOPIS_LIST_URL = "http://www.kopis.or.kr/openApi/restful/pblprfr";

  // KOPIS 축제목록(prffest) API - 요청 파라미터/응답 구조가 공연목록과 완전히 동일하고 festival=Y로
  // 태그된 항목만 내려준다는 점만 다르다. 같은 서비스키로 별도 신청 없이 바로 호출 가능함(실측 확인).
  private static final String KOPIS_FESTIVAL_URL = "http://kopis.or.kr/openApi/restful/prffest";

  // KOPIS 공연시설 상세조회 API - mt10id로 주소/위경도를 얻기 위해 사용
  private static final String KOPIS_FACILITY_URL = "http://www.kopis.or.kr/openApi/restful/prfplc";

  // KOPIS 박스오피스(예매율 순위) API - 실측으로 파라미터명을 확인함(date가 아니라 stdate/eddate,
  // catecode/area는 선택). ststype=day면 stdate=eddate를 같은 날짜로 줘서 "그날의 순위"를 받는다.
  private static final String KOPIS_BOXOFFICE_URL = "http://www.kopis.or.kr/openApi/restful/boxoffice";

  // 박스오피스용 장르 코드(catecode) - KOPIS 공식 오픈API 코드표(kopis_openapi_code_v3.1.docx)에서
  // 확인함. catecode 없이 호출하면 "전체 통합 순위"만 나와서 순위권 밖 장르(국악/아동 등)가 아예 안
  // 잡히므로, 장르별로 따로 호출해서 각 장르 안에서의 순위(rnum)를 얻는다(2026-08-19). 코드표에
  // "CCCA|CCCB"처럼 같은 장르 설명에 코드 2개가 같이 적힌 경우가 있어(클래식/오페라, 무용, 국악/복합)
  // 둘 다 따로 호출한다 - 어느 쪽이 실제로 쓰이는 코드인지 문서만으로는 확정할 수 없어서 안전하게 둘 다
  // 조회. 참고: 이 코드표엔 "대중음악(콘서트)" 항목이 아예 없음 - KOPIS 박스오피스가 다루는 예매율은
  // 연극/뮤지컬/클래식·오페라/무용/국악·복합/아동/오픈런 7개 그룹뿐이라, 콘서트는 박스오피스 순위
  // 자체를 제공하지 않는다(우리가 빠뜨린 게 아니라 API 자체의 한계).
  private static final List<String> BOXOFFICE_GENRE_CODES = List.of(
      "AAAA", // 연극
      "AAAB", // 뮤지컬
      "CCCA", "CCCB", // 클래식/오페라
      "BBBA", "BBBB", // 무용
      "CCCC", "EEEA", // 국악/복합
      "KID",  // 아동
      "OPEN"  // 오픈런
  );

  // 우리 DB에서 "이 데이터가 KOPIS에서 왔다"는 걸 표시하기 위한 값 (Place.externalSource에 저장됨)
  private static final String EXTERNAL_SOURCE = "KOPIS";

  // 커플 데이트 앱 취지와 안 맞는 어린이/키즈 대상 공연은 제외한다 - PlaceCategorySeeder에서
  // "어린이체험관"을 뺀 것과 같은 이유(2026-08-19). 실측 결과 뮤지컬 카테고리에 "어린이 캣",
  // "MBC 심야괴담회X니니키즈: 학교에서 살아남기" 같은 아동용 공연이 6건 섞여 있었음.
  private static final List<String> KIDS_CONTENT_KEYWORDS = List.of("어린이", "키즈", "유아", "아동극");

  private boolean isKidsContent(String title) {
    return title != null && KIDS_CONTENT_KEYWORDS.stream().anyMatch(title::contains);
  }

  private final PlaceRepository placeRepository;
  private final PerformanceRankingRepository performanceRankingRepository;
  private final PlaceStyleRepository placeStyleRepository;

  // 외부 API를 HTTP로 호출할 때 쓰는 스프링 기본 도구. new로 직접 생성해도 됨 (설정이 단순한 경우)
  private final RestTemplate restTemplate = new RestTemplate();

  // application.yaml의 kopis.service-key 값을 그대로 주입받음 (yaml -> .env의 KOPIS_SERVICE_KEY로 연결됨)
  @Value("${kopis.service-key}")
  private String serviceKey;

  /**
   * 매일 새벽 3시에 자동 실행됨 (cron 표현식: 초 분 시 일 월 요일).
   * cron이 실제로 동작하려면 메인 애플리케이션 클래스에 @EnableScheduling이 붙어있어야 함.
   */
  @Scheduled(cron = "0 0 3 * * *")
  @Override
  public void syncPerformances() {
    LocalDate today = LocalDate.now();
    // 원래 30일이었는데(공연 수가 900건대로 적었음), 너무 멀리 있는 공연까지 보여주면 티켓 오픈 전이거나
    // 일정이 바뀔 수 있다는 트레이드오프를 감안해 60일로만 늘림.
    List<KopisPerformanceDto> performances = fetchFromKopis(KOPIS_LIST_URL, today, today.plusDays(60));

    // 장르(genrenm)를 그대로 카테고리로 사용 - 뮤지컬/연극/국악 등
    UpsertResult result = upsertAll(performances, KopisPerformanceDto::genrenm);

    log.info("KOPIS 공연 동기화 완료 - 신규 {}건, 갱신 {}건 (전체 조회 {}건)",
        result.created(), result.updated(), performances.size());
  }

  /**
   * KOPIS 축제목록(prffest) 동기화 - 공연목록과 요청/응답 구조가 동일하지만 festival=Y로 태그된
   * 항목만 내려준다. 장르 대신 "축제" 하나로 카테고리를 고정해서, 데이트 코스 추천 시 일반 공연과
   * 구분해서 보여줄 수 있게 한다. 같은 mt20id를 syncPerformances가 이미 저장해뒀으면(장르 기준
   * 카테고리로) 이 동기화가 나중에 실행되면서 "축제"로 덮어써 더 구체적인 정보로 갱신된다.
   */
  @Scheduled(cron = "0 30 3 * * *") // 공연 동기화(3시) 직후, 30분 뒤로 배치해 겹치지 않게 함
  @Override
  public void syncFestivals() {
    LocalDate today = LocalDate.now();
    List<KopisPerformanceDto> festivals = fetchFromKopis(KOPIS_FESTIVAL_URL, today, today.plusDays(60));

    UpsertResult result = upsertAll(festivals, p -> "축제");

    log.info("KOPIS 축제 동기화 완료 - 신규 {}건, 갱신 {}건 (전체 조회 {}건)",
        result.created(), result.updated(), festivals.size());
  }

  /**
   * KOPIS 박스오피스(예매율 순위) 동기화 - 공연/축제 동기화 직후에 돌아야 그날 새로 들어온 공연도
   * 순위표에서 바로 매칭될 수 있어서 3시 40분으로 배치했다(공연 3시, 축제 3시30분 다음).
   * 순위는 매일 바뀌므로 upsert 없이 전체 삭제 후 다시 채운다(검색 기반 소스와 동일한 이유).
   * 아직 우리 DB에 없는 공연(mt20id 불일치)은 순위표에서 조용히 건너뛴다 - 다음날 공연 동기화가
   * 그 공연을 채워 넣으면 그 다음 순위 동기화 때 자연스럽게 잡힌다.
   */
  @Scheduled(cron = "0 40 3 * * *")
  @Override
  public void syncBoxOffice() {
    // 박스오피스는 당일 데이터가 아직 집계 중일 수 있어(실측 확인) 전날(어제) 기준으로 조회한다.
    LocalDate yesterday = LocalDate.now().minusDays(1);

    performanceRankingRepository.deleteAllInBatch();

    int totalFetched = 0;
    int linked = 0;
    // 장르 코드(BOXOFFICE_GENRE_CODES)마다 따로 호출한다 - 그러면 rnum이 "전체 순위"가 아니라
    // "그 장르 안에서의 순위"로 내려오므로, 장르별로 나눠서 보여주는 게 목적일 때 이렇게 해야 한다.
    for (String catecode : BOXOFFICE_GENRE_CODES) {
      List<KopisBoxOfficeDto> ranking = fetchBoxOffice(yesterday, catecode);
      totalFetched += ranking.size();

      for (KopisBoxOfficeDto item : ranking) {
        Optional<Place> place = placeRepository.findByExternalSourceAndExternalId(EXTERNAL_SOURCE, item.mt20id());
        if (place.isEmpty()) {
          continue;
        }
        Integer rankNo = parseIntSafe(item.rnum());
        if (rankNo == null) {
          continue;
        }
        performanceRankingRepository.save(PerformanceRanking.builder()
            .place(place.get())
            .rankNo(rankNo)
            .genreName(item.cate())
            .area(item.area())
            .build());
        linked++;
      }

      sleepBriefly();
    }

    log.info("KOPIS 박스오피스 순위 동기화 완료 - 장르 {}개 조회, 전체 {}건 중 Place 매칭 {}건",
        BOXOFFICE_GENRE_CODES.size(), totalFetched, linked);
  }

  /** KOPIS 박스오피스 API를 호출해서 지정한 날짜·장르(catecode)의 예매율 순위를 받아옴 */
  private List<KopisBoxOfficeDto> fetchBoxOffice(LocalDate date, String catecode) {
    String d = date.format(DateTimeFormatter.BASIC_ISO_DATE);
    String encodedServiceKey = java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8);
    String url = UriComponentsBuilder.fromUriString(KOPIS_BOXOFFICE_URL)
        .queryParam("service", encodedServiceKey)
        .queryParam("ststype", "day")
        .queryParam("stdate", d)
        .queryParam("eddate", d)
        .queryParam("catecode", catecode)
        .build(true)
        .toUriString();

    byte[] xmlBytes = restTemplate.getForObject(java.net.URI.create(url), byte[].class);
    return parseBoxOffice(xmlBytes);
  }

  /** KOPIS 박스오피스 응답 구조: <boxofs><boxof>...순위 1건...</boxof>...</boxofs> */
  private List<KopisBoxOfficeDto> parseBoxOffice(byte[] xmlBytes) {
    List<KopisBoxOfficeDto> result = new ArrayList<>();
    Document doc = parseXml(xmlBytes);
    if (doc == null) return result;

    NodeList items = doc.getElementsByTagName("boxof");
    for (int i = 0; i < items.getLength(); i++) {
      Element el = (Element) items.item(i);
      result.add(new KopisBoxOfficeDto(
          text(el, "mt20id"),
          text(el, "prfnm"),
          text(el, "cate"),
          text(el, "area"),
          text(el, "rnum")
      ));
    }
    return result;
  }

  private Integer parseIntSafe(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private record UpsertResult(int created, int updated) {
  }

  /** 공연/축제 목록을 upsert하는 공통 로직. categoryFn으로 이 목록에 적용할 카테고리를 결정한다. */
  private UpsertResult upsertAll(List<KopisPerformanceDto> performances,
                                  java.util.function.Function<KopisPerformanceDto, String> categoryFn) {
    // 같은 공연시설(mt10id)을 여러 공연이 공유하는 경우가 많아, 시설 상세조회는 한 번만 호출하고 캐시해서 재사용
    Map<String, KopisFacilityDto> facilityCache = new HashMap<>();

    int created = 0;
    int updated = 0;

    for (KopisPerformanceDto p : performances) {
      // 이미 저장된 공연인지, external_source + external_id 조합으로 확인 (KOPIS의 mt20id가 고유 식별자)
      Optional<Place> existing =
          placeRepository.findByExternalSourceAndExternalId(EXTERNAL_SOURCE, p.mt20id());

      if (isKidsContent(p.prfnm())) {
        // 이 필터가 생기기 전에 이미 저장돼 있던 것도 이번 동기화 때 같이 정리한다. place_styles(1:1)와
        // performance_rankings(N:1)가 FK로 places를 참조하고 있어서, 자식부터 지워야 places 삭제 시
        // ORA-02292(무결성 제약조건 위배)가 안 난다(TourApiSyncService.cleanupBlacklistedPlaces와 동일 이유).
        existing.ifPresent(place -> {
          performanceRankingRepository.findByPlace_IdIn(List.of(place.getId())).forEach(performanceRankingRepository::delete);
          placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
          placeRepository.delete(place);
        });
        continue;
      }

      // 상세조회 하나로 공연시설 ID(mt10id)뿐 아니라 공연시간/가격/예매링크까지 같이 얻는다
      // (추가 API 호출 없이 기존에 어차피 부르던 상세조회 응답을 더 활용, 2026-08-13).
      KopisPerformanceDetailDto detail = fetchDetail(p.mt20id());
      String mt10id = detail != null ? detail.mt10id() : null;
      KopisFacilityDto facility = mt10id == null
          ? null
          : facilityCache.computeIfAbsent(mt10id, this::fetchFacility);

      String address = facility != null ? facility.adres() : null;
      Double lat = facility != null ? parseCoordinate(facility.la()) : null;
      Double lng = facility != null ? parseCoordinate(facility.lo()) : null;
      String category = categoryFn.apply(p);
      LocalDate startDate = parseKopisDate(p.prfpdfrom());
      LocalDate endDate = parseKopisDate(p.prfpdto());
      Integer openRun = "Y".equals(p.openrun()) ? 1 : 0;

      if (existing.isPresent()) {
        // 이미 있으면 새로 만들지 않고, 최신 값으로 덮어씀 (update)
        Place place = existing.get();
        place.setName(p.prfnm());
        place.setCategory(category);
        place.setImageUrl(p.poster());
        if (address != null) place.setAddress(address);
        if (lat != null) place.setLatitude(lat);
        if (lng != null) place.setLongitude(lng);
        place.setStartDate(startDate);
        place.setEndDate(endDate);
        place.setIsOpenRun(openRun);
        place.setPerformanceState(p.prfstate());
        if (detail != null) {
          place.setRuntimeText(detail.prfruntime());
          place.setPriceInfo(detail.pcseguidance());
          place.setShowTimeInfo(detail.dtguidance());
          place.setBookingUrl(detail.bookingUrl());
        }
        placeRepository.save(place);
        updated++;
      } else {
        // 처음 보는 공연이면 새 Place 행을 만듦 (insert)
        Place place = Place.builder()
            .name(p.prfnm())
            .category(category)
            .address(address)
            .latitude(lat)
            .longitude(lng)
            .imageUrl(p.poster())
            .externalSource(EXTERNAL_SOURCE)
            .externalId(p.mt20id())
            .startDate(startDate)
            .endDate(endDate)
            .isOpenRun(openRun)
            .performanceState(p.prfstate())
            .runtimeText(detail != null ? detail.prfruntime() : null)
            .priceInfo(detail != null ? detail.pcseguidance() : null)
            .showTimeInfo(detail != null ? detail.dtguidance() : null)
            .bookingUrl(detail != null ? detail.bookingUrl() : null)
            .build();
        placeRepository.save(place);
        created++;
      }
    }

    return new UpsertResult(created, updated);
  }

  /** KOPIS API를 호출해서 앞으로 30일간의 공연 목록을 받아옴 */
  // KOPIS가 한 번에 내려주는 최대 건수(그 이상 요청하면 "최대 조회수는 100건까지 가능합니다" 에러가 남)
  private static final int KOPIS_PAGE_SIZE = 100;

  // 무한 루프 방지용 안전장치. 페이지당 100건 * 30페이지 = 최대 3000건까지 수집 (평소 30일치가
  // 600~700건 수준으로 확인되어 충분히 여유 있게 잡음)
  private static final int KOPIS_MAX_PAGES = 30;

  /**
   * KOPIS는 한 번의 요청으로 최대 100건만 내려주기 때문에(rows 파라미터 상한), 조회 기간의
   * 공연이 100건을 넘으면 cpage(페이지 번호)를 1씩 늘려가며 여러 번 호출해서 전부 모아야 한다.
   * "이번 페이지 응답이 정확히 100건"이면 다음 페이지에 더 남아있을 가능성이 있다는 뜻이고,
   * 100건보다 적게(또는 0건) 오면 그게 마지막 페이지라는 뜻이라 그때 반복을 멈춘다.
   *
   * baseUrl만 다르면 공연목록(pblprfr)/축제목록(prffest) 둘 다 요청/응답 구조가 완전히 같아서
   * 이 메서드 하나로 공유해서 쓴다.
   */
  private List<KopisPerformanceDto> fetchFromKopis(String baseUrl, LocalDate start, LocalDate end) {
    // KOPIS는 날짜를 "yyyyMMdd" 형식 문자열로 받음 (예: 20260801)
    String stdate = start.format(DateTimeFormatter.BASIC_ISO_DATE);
    String eddate = end.format(DateTimeFormatter.BASIC_ISO_DATE);

    List<KopisPerformanceDto> all = new ArrayList<>();

    for (int page = 1; page <= KOPIS_MAX_PAGES; page++) {
      // UriComponentsBuilder.encode()는 RFC 3986 기준으로 +,/를 인코딩 안 하는데, 공공데이터포털
      // 서버는 +를 공백으로 오해석해서 키가 깨질 수 있다(TourAPI에서 실제로 이 문제로 인증 실패했었음).
      // serviceKey만 URLEncoder로 미리 인코딩하고 build(true)로 이중 인코딩을 막는다.
      String encodedServiceKey = java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8);
      String url = UriComponentsBuilder.fromUriString(baseUrl)
          .queryParam("service", encodedServiceKey)
          .queryParam("stdate", stdate)
          .queryParam("eddate", eddate)
          .queryParam("cpage", page)
          .queryParam("rows", KOPIS_PAGE_SIZE)
          .build(true)
          .toUriString();

      // GET 요청을 보내고, 응답 body를 바이트 그대로 받음.
      // getForObject(String, ...)는 이미 인코딩된 URL을 다시 인코딩해버리는(이중 인코딩) 문제가 있어
      // URI로 직접 넘긴다 (TourAPI에서 이 문제로 실제 인증 실패를 겪었음).
      // String.class로 받지 않는 이유: KOPIS 응답의 Content-Type이 charset 없이 "application/xml"만
      // 내려와서, RestTemplate이 한글을 엉뚱한 인코딩으로 디코딩해 깨트리는 문제가 있었다(실측 확인).
      // XML 자체엔 <?xml ... encoding="UTF-8"?> 선언이 정확히 들어있으므로, 바이트를 그대로 XML
      // 파서에 넘겨서 파서가 그 선언을 보고 알아서 정확히 디코딩하도록 한다.
      byte[] xmlBytes = restTemplate.getForObject(java.net.URI.create(url), byte[].class);
      List<KopisPerformanceDto> pageItems = parsePerformances(xmlBytes);
      all.addAll(pageItems);

      if (pageItems.size() < KOPIS_PAGE_SIZE) {
        break; // 마지막 페이지까지 다 읽음
      }
    }

    return all;
  }

  /**
   * 공연 상세조회(mt20id)를 호출해 공연시설 ID(mt10id)뿐 아니라 공연시간/가격안내/요일별시간표/
   * 예매링크까지 한 번에 얻어옴 - 원래 mt10id만 쓰던 걸 그대로 확장(2026-08-13, 추가 API 호출 없음).
   */
  private KopisPerformanceDetailDto fetchDetail(String mt20id) {
    String url = UriComponentsBuilder.fromUriString(KOPIS_LIST_URL + "/" + mt20id)
        .queryParam("service", java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8))
        .build(true)
        .toUriString();

    try {
      // getForObject(String, ...)는 이미 인코딩된 URL을 다시 인코딩해버리는(이중 인코딩) 문제가 있어
      // URI로 직접 넘긴다 (TourAPI에서 이 문제로 실제 인증 실패를 겪었음).
      // byte[]로 받는 이유는 fetchPerformances의 주석 참고 (charset 없는 Content-Type 때문에 한글이 깨짐).
      byte[] xmlBytes = restTemplate.getForObject(java.net.URI.create(url), byte[].class);
      Document doc = parseXml(xmlBytes);
      if (doc == null) return null;

      NodeList items = doc.getElementsByTagName("db");
      if (items.getLength() == 0) return null;

      Element el = (Element) items.item(0);
      // 예매링크는 <relates><relate><relateurl>...</relateurl></relate>...</relates> 구조로,
      // 여러 예매처가 있을 수 있어 그중 첫 번째만 사용한다(없으면 null).
      NodeList relateUrls = el.getElementsByTagName("relateurl");
      String bookingUrl = relateUrls.getLength() > 0 ? relateUrls.item(0).getTextContent() : null;

      return new KopisPerformanceDetailDto(
          text(el, "mt10id"),
          text(el, "prfruntime"),
          text(el, "pcseguidance"),
          text(el, "dtguidance"),
          bookingUrl
      );
    } catch (Exception e) {
      log.error("KOPIS 공연 상세조회 실패 (mt20id={})", mt20id, e);
      return null;
    }
  }

  /** 공연시설 상세조회(mt10id)를 호출해 주소/위경도를 얻어옴 */
  private KopisFacilityDto fetchFacility(String mt10id) {
    String url = UriComponentsBuilder.fromUriString(KOPIS_FACILITY_URL + "/" + mt10id)
        .queryParam("service", java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8))
        .build(true)
        .toUriString();

    try {
      // getForObject(String, ...)는 이미 인코딩된 URL을 다시 인코딩해버리는(이중 인코딩) 문제가 있어
      // URI로 직접 넘긴다 (TourAPI에서 이 문제로 실제 인증 실패를 겪었음).
      // byte[]로 받는 이유는 fetchPerformances의 주석 참고 (charset 없는 Content-Type 때문에 한글이 깨짐).
      byte[] xmlBytes = restTemplate.getForObject(java.net.URI.create(url), byte[].class);
      Document doc = parseXml(xmlBytes);
      if (doc == null) return null;

      NodeList items = doc.getElementsByTagName("db");
      if (items.getLength() == 0) return null;

      Element el = (Element) items.item(0);
      return new KopisFacilityDto(mt10id, text(el, "adres"), text(el, "la"), text(el, "lo"));
    } catch (Exception e) {
      log.error("KOPIS 공연시설 상세조회 실패 (mt10id={})", mt10id, e);
      return null;
    }
  }

  /** KOPIS가 돌려준 XML을 파싱해서 우리가 쓰기 편한 객체 리스트로 바꿔줌 */
  private List<KopisPerformanceDto> parsePerformances(byte[] xmlBytes) {
    List<KopisPerformanceDto> result = new ArrayList<>();
    Document doc = parseXml(xmlBytes);
    if (doc == null) return result;

    // KOPIS 응답 구조: <dbs><db>...공연 1건...</db><db>...공연 2건...</db></dbs>
    // "db" 태그 하나하나가 공연 1건에 해당함
    NodeList items = doc.getElementsByTagName("db");

    for (int i = 0; i < items.getLength(); i++) {
      Element el = (Element) items.item(i);
      result.add(new KopisPerformanceDto(
          text(el, "mt20id"),
          text(el, "prfnm"),
          text(el, "fcltynm"),
          text(el, "poster"),
          text(el, "genrenm"),
          text(el, "prfpdfrom"),
          text(el, "prfpdto"),
          text(el, "openrun"),
          text(el, "prfstate")
      ));
    }

    return result;
  }

  /**
   * XML 바이트를 DOM Document로 파싱하는 공통 로직 (악성 XML 방지를 위해 DOCTYPE 선언 금지).
   * 문자열이 아니라 바이트를 그대로 넘기는 이유: XML 파서는 바이트 스트림을 받으면 XML 선언의
   * encoding 속성(KOPIS는 UTF-8로 정확히 선언함)을 보고 스스로 올바르게 디코딩한다. RestTemplate으로
   * 미리 String으로 변환해서 넘기면, Content-Type에 charset이 없어 엉뚱한 인코딩으로 디코딩되어
   * 한글이 깨지는 문제가 있었다(실측 확인 - genrenm/prfnm 등 한글 필드가 DB에 깨진 채 저장됨).
   */
  private Document parseXml(byte[] xmlBytes) {
    if (xmlBytes == null || xmlBytes.length == 0) {
      return null;
    }

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new InputSource(new java.io.ByteArrayInputStream(xmlBytes)));
    } catch (Exception e) {
      log.error("KOPIS 응답 파싱 실패", e);
      return null;
    }
  }

  /** <db> 안에서 특정 태그(tag)의 텍스트 값만 꺼내는 헬퍼 메서드 */
  private String text(Element parent, String tag) {
    NodeList nl = parent.getElementsByTagName(tag);
    return nl.getLength() > 0 ? nl.item(0).getTextContent() : null;
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // KOPIS 날짜는 "2026.08.15" 형식(yyyy.MM.dd)으로 내려온다.
  private static final DateTimeFormatter KOPIS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

  private LocalDate parseKopisDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value.trim(), KOPIS_DATE_FORMAT);
    } catch (Exception e) {
      return null;
    }
  }

  // 박스오피스를 장르별로 나눠 여러 번 호출할 때 KOPIS 서버에 과도하게 연속 요청하지 않도록 잠깐 쉼
  // (KakaoPlaceSyncService와 동일한 패턴).
  private void sleepBriefly() {
    try {
      Thread.sleep(150);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

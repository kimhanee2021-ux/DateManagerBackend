package org.ict.datemanagerbackend.domain.place.service;

import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.TourApiPlaceDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한국관광공사 TourAPI(국문 관광정보서비스, KorService2)의 지역기반 목록조회(areaBasedList2)에서
 * 장소 데이터를 받아와 places 테이블에 채워 넣는 서비스.
 *
 * PlaceSyncService(KOPIS 공연 동기화)와 동일하게 external_source+external_id로 upsert한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TourApiSyncServiceImpl implements TourApiSyncService {

  private static final String LIST_URL = "https://apis.data.go.kr/B551011/KorService2/areaBasedList2";

  // 우리 DB에서 "이 데이터가 TourAPI에서 왔다"는 걸 표시하기 위한 값 (Place.externalSource에 저장됨)
  private static final String EXTERNAL_SOURCE = "TOURAPI";

  // TourAPI 콘텐츠타입ID -> 우리 서비스 카테고리명 (contenttypeid는 목록 API에서 그대로 내려주지 않는 경우가
  // 있어, 호출할 때 지정한 contentTypeId를 그대로 카테고리로 사용한다)
  private static final Map<String, String> CATEGORY_BY_CONTENT_TYPE = Map.of(
      "12", "관광지",
      "14", "문화시설",
      "15", "공연",
      "28", "액티비티",
      "32", "숙박", // CSV로 임포트한 숙박(문화_숙박업.csv)엔 사진이 없어서, 사진 있는 숙박 데이터를 이걸로 보강
      "38", "쇼핑",
      "39", "맛집"
  );

  // 전국 어디서나 지점이 반복되는 프랜차이즈/체인점은 "데이트 장소"로서 변별력이 없어 목록을 도배하므로
  // 이름에 이 키워드가 포함되면 동기화 자체를 건너뛴다(특히 쇼핑 카테고리에 올리브영 등이 지점마다
  // 중복 노출되던 문제, 2026-08-14).
  // "약국"은 TourAPI가 쇼핑(38) 카테고리로 잘못 분류해서 들어오는 경우(2026-08-18 확인, 227건)
  // - 데이트 장소가 아니라서 프랜차이즈 편의점/올리브영과 같은 이유로 제외한다.
  // 2026-08-19 추가분: 쇼핑 카테고리 실측 결과 나이키/유니클로 같은 패션 프랜차이즈, 롯데하이마트
  // 같은 가전 체인, 이마트/롯데마트/홈플러스 같은 대형마트도 전국에 반복 등장해서 같은 기준으로 추가.
  // 대형마트를 뺀 이유: "OO동 이마트" 자체가 데이트 코스가 되기보다 장보기 목적이 커서 위 편의점류와
  // 성격이 같다고 판단(백화점처럼 그 장소 자체가 나들이 목적지는 아님).
  private static final List<String> BLACKLISTED_NAME_KEYWORDS = List.of(
      "올리브영", "다이소", "이마트24", "GS25", "CU", "세븐일레븐", "미니스톱", "약국",
      "롯데하이마트", "아트박스", "정관장", "LG전자 베스트샵", "이마트", "노브랜드", "롯데마트",
      "홈플러스", "유니클로", "다비치안경", "오렌즈", "나이키", "노스페이스", "이니스프리",
      "으뜸50안경", "뉴발란스", "코오롱스포츠", "에잇세컨즈", "스파오", "ABC마트"
  );

  private boolean isBlacklisted(String name) {
    return name != null && BLACKLISTED_NAME_KEYWORDS.stream().anyMatch(name::contains);
  }

  // TourAPI가 쇼핑(38)에 성형외과/피부과/치과 등 병의원을 잘못 섞어서 준다(실측 106건, "디아이성형외과의원"
  // "닥터스피부과의원" 등 전부 진짜 병원 - 2026-08-19). 데이트 앱 취지와 정반대라 제외해야 하는데, 전체
  // BLACKLISTED_NAME_KEYWORDS에 넣지 않고 쇼핑 카테고리에서만 걸러낸다 - "의원"/"병원"은 "안의원조갈비집"
  // (맛집), "부산 구 백제병원"(관광지, 근대문화유산), "OO커피 XX병원점"(카페가 병원 근처에 있을 뿐)처럼
  // 다른 카테고리에서는 진짜 데이트 장소 이름에 우연히 포함되는 경우가 있어서 전역으로 걸면 오탐이 난다.
  private static final List<String> MEDICAL_KEYWORDS = List.of("의원", "병원");

  private boolean isMedicalFacility(String name) {
    return name != null && MEDICAL_KEYWORDS.stream().anyMatch(name::contains);
  }

  // 안경점(체인 몇 개는 이미 BLACKLISTED_NAME_KEYWORDS에 있었지만 개별 매장이 훨씬 많음)과
  // 담배/전자담배 판매점도 데이트 장소가 아니라서 제외한다(2026-08-19, 사용자 요청). 위 병의원과
  // 같은 이유로 쇼핑 카테고리에서만 걸러낸다 - "안경"은 다른 카테고리에서 지명 등에 우연히
  // 섞일 가능성을 배제할 수 없어서다. 실측으로 "안경"/"담배"/"베이프" 세 키워드 모두 오탐(예:
  // "글로벌"처럼 무관한 단어에 걸리는 경우) 없이 깔끔하게 골라짐을 확인함.
  private static final List<String> EXCLUDED_SHOPPING_KEYWORDS = List.of("안경", "담배", "베이프");

  private boolean isExcludedShoppingKeyword(String name) {
    return name != null && EXCLUDED_SHOPPING_KEYWORDS.stream().anyMatch(name::contains);
  }

  // 백화점/아울렛 안에 입점한 개별 브랜드 매장(예: "로에베 현대백화점 압구정본점",
  // "스케쳐스 롯데아울렛 동부산점")이 TourAPI 쇼핑(38) 카테고리에 통째로 섞여 들어온다 - 실측 결과
  // 고유 백화점 지점 65곳에 딸린 브랜드 매장이 1,791건, 아울렛 쪽도 같은 패턴으로 다수 확인됨
  // (2026-08-19). 데이트 코스로는 "그 백화점/아울렛" 자체 하나면 충분하므로, 이름이 "백화점" 또는
  // "아울렛"을 포함해도 아래 접두어로 시작하지 않으면(=쇼핑몰 자체가 아니라 그 안의 특정 브랜드
  // 매장이면) 건너뛴다.
  private static final List<String> MALL_PREFIXES = List.of(
      // 백화점
      "현대백화점", "롯데백화점", "신세계백화점", "갤러리아", "AK플라자", "대구백화점", "동아백화점",
      // 아울렛
      "롯데아울렛", "롯데프리미엄아울렛", "모다아울렛", "신세계사이먼프리미엄아울렛",
      "현대프리미엄아울렛", "뉴코아아울렛", "뉴코아팩토리아울렛", "2001아울렛",
      // 복합쇼핑몰 - 이름에 "백화점"/"아울렛"이 안 들어가서 위 두 그룹과 같은 문제를 겪고 있었다
      // (2026-08-20, 사용자가 IFC몰/롯데몰/타임스퀘어/타임빌라스에서 브랜드 매장 중복을 직접 확인해서 알려줌).
      // 타임빌라스는 롯데가 기존 롯데몰 일부 지점을 리브랜딩한 이름이라 롯데몰과 별개로 둔다.
      "IFC몰", "롯데몰", "타임스퀘어", "타임빌라스"
  );

  // 스타필드도 같은 문제라 여기 합쳤다(2026-08-19) - "스타필드"가 들어간 쇼핑 카테고리 장소 144건을
  // 실제로 까보니 전부 "브랜드명 스타필드 하남점"처럼 안의 개별 매장이고, 몰 자체("스타필드 하남" 등)는
  // 단 하나도 없었다(스타필드는 TourAPI가 몰 자체를 별도 장소로 안 주는 듯). 그래서 MALL_PREFIXES에
  // 넣어서 예외로 지켜줄 이름이 없어 - "스타필드"가 들어간 쇼핑 카테고리는 전부 걸러낸다.
  private boolean isBrandInsideMall(String name) {
    if (name == null) {
      return false;
    }
    boolean looksLikeMallBrandEntry = name.contains("백화점") || name.contains("아울렛")
        || name.contains("스타필드") || name.contains("IFC몰") || name.contains("롯데몰")
        || name.contains("타임스퀘어") || name.contains("타임빌라스");
    if (!looksLikeMallBrandEntry) {
      return false;
    }
    return MALL_PREFIXES.stream().noneMatch(name::startsWith);
  }

  // 쇼핑(38) 카테고리는 위 두 필터로도 다 못 잡을 만큼 프랜차이즈 브랜드 종류가 많다(실측 결과
  // "브랜드명 + 지점명" 패턴으로 전국 5곳 이상 반복되는 브랜드가 128개, 1,277건 - 2026-08-19).
  // 브랜드를 일일이 나열하는 대신, 이름 첫 단어(대체로 브랜드명, 예: "로이드 안동점" -> "로이드")를
  // 기준으로 전국 등장 횟수를 세어 이 기준 이상이면 프랜차이즈로 보고 통째로 제외한다. 실측으로 확인한
  // 임계값(사용자 지정, 2026-08-19): 5회 이상.
  private static final int SHOPPING_FRANCHISE_THRESHOLD = 5;

  private String brandKey(String name) {
    if (name == null || name.isBlank()) return "";
    return name.trim().split("\\s+")[0];
  }

  /**
   * 쇼핑 목록 전체에서 이름 첫 단어 기준 SHOPPING_FRANCHISE_THRESHOLD회 이상 반복되는 브랜드 키 집합을
   * 만든다. "백화점"/"아울렛"이 들어간 이름은 제외한다 - 그건 isBrandInsideMall이 이미 따로 판단하는
   * 영역이라, 여기서 또 세면 "현대백화점"처럼 정상적으로 남겨야 할 이름까지 프랜차이즈로 오판해서
   * 지워버릴 수 있다(예: 현대백화점 지점만 59개라 5회 기준을 그냥 넘어감).
   */
  private java.util.Set<String> detectFranchiseBrandKeys(List<TourApiPlaceDto> shoppingPlaces) {
    Map<String, Long> counts = shoppingPlaces.stream()
        .filter(p -> p.title() != null && !p.title().contains("백화점") && !p.title().contains("아울렛"))
        .collect(java.util.stream.Collectors.groupingBy(p -> brandKey(p.title()), java.util.stream.Collectors.counting()));
    return counts.entrySet().stream()
        .filter(e -> e.getValue() >= SHOPPING_FRANCHISE_THRESHOLD)
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toSet());
  }

  private final PlaceRepository placeRepository;
  private final PlaceStyleRepository placeStyleRepository;
  private final PlaceDedupService placeDedupService;
  private final RestTemplate restTemplate = new RestTemplate();

  // application.yaml의 tourapi.service-key 값을 그대로 주입받음 (yaml -> .env의 TOUR_API_SERVICE_KEY로 연결됨)
  @Value("${tourapi.service-key}")
  private String serviceKey;

  /**
   * 매일 새벽 4시에 자동 실행됨 (KOPIS 동기화가 새벽 3시라 시간을 겹치지 않게 뒀다).
   */
  @Scheduled(cron = "0 0 4 * * *")
  @Override
  public void syncPlaces() {
    int created = 0;
    int updated = 0;

    for (Map.Entry<String, String> entry : CATEGORY_BY_CONTENT_TYPE.entrySet()) {
      String contentTypeId = entry.getKey();
      String category = entry.getValue();
      List<TourApiPlaceDto> places = fetchPlaces(contentTypeId);

      // 쇼핑만 프랜차이즈 브랜드 종류가 지나치게 많아서(실측 128개 이상) 정적 키워드로는 못 잡는다 -
      // "쇼핑"일 때만 이번 목록 전체를 먼저 훑어 반복 브랜드를 찾아둔다(2026-08-19).
      java.util.Set<String> franchiseBrandKeys = "쇼핑".equals(category)
          ? detectFranchiseBrandKeys(places)
          : java.util.Set.of();

      boolean isShoppingCategory = "쇼핑".equals(category);

      for (TourApiPlaceDto p : places) {
        if (isBlacklisted(p.title()) || isBrandInsideMall(p.title()) || franchiseBrandKeys.contains(brandKey(p.title()))
            || (isShoppingCategory && (isMedicalFacility(p.title()) || isExcludedShoppingKeyword(p.title())))) {
          continue;
        }

        Optional<Place> existing =
            placeRepository.findByExternalSourceAndExternalId(EXTERNAL_SOURCE, p.contentid());

        Double lat = parseCoordinate(p.mapy());
        Double lng = parseCoordinate(p.mapx());
        String image = !p.firstimage().isBlank() ? p.firstimage() : p.firstimage2();
        String address = PlaceAddressNormalizer.fix(
            p.addr2().isBlank() ? p.addr1() : (p.addr1() + " " + p.addr2()).trim());

        if (existing.isPresent()) {
          Place place = existing.get();
          place.setName(p.title());
          place.setCategory(category);
          place.setAddress(address);
          // coordinateVerified=true인 장소는 PlaceCoordinateVerificationService가 카카오로 이미
          // 좌표를 검증/교정해둔 상태다(2026-08-20, "홍원" 좌표 오차 발견 후 도입) - 여기서 TourAPI
          // 원본 mapx/mapy로 다시 덮어쓰면 매일 새벽 4시마다 교정한 좌표가 원래의 잘못된 값으로
          // 되돌아가버린다. 검증 전(NULL/false)인 장소만 원본 값을 그대로 반영한다.
          if (!Boolean.TRUE.equals(place.getCoordinateVerified())) {
            place.setLatitude(lat);
            place.setLongitude(lng);
          }
          place.setImageUrl(image);
          placeRepository.save(place);
          updated++;
          continue;
        }

        // external_source+external_id로는 못 걸러낸다 - 같은 실제 장소가 다른 소스(KOPIS/네이버/카카오
        // 등)에서 이미 저장돼 있을 수 있으므로 이름+좌표로 한 번 더 확인한다.
        Optional<Place> duplicate = placeDedupService.findDuplicate(p.title(), lat, lng);
        if (duplicate.isPresent()) {
          Place place = duplicate.get();
          if (place.getImageUrl() == null || place.getImageUrl().isBlank()) {
            place.setImageUrl(image); // 이미지 없던 기존 장소면 이번 소스의 이미지로 채워줌
            placeRepository.save(place);
          }
          updated++;
        } else {
          Place place = Place.builder()
              .name(p.title())
              .category(category)
              .address(address)
              .latitude(lat)
              .longitude(lng)
              .imageUrl(image)
              .externalSource(EXTERNAL_SOURCE)
              .externalId(p.contentid())
              .build();
          placeRepository.save(place);
          created++;
        }
      }
    }

    log.info("TourAPI 장소 동기화 완료 - 신규 {}건, 갱신 {}건", created, updated);

    // TODO: 전화번호, 가격/주차 정보는 이 목록 API(areaBasedList2)에는 안 들어있음.
    //       필요하면 contentid로 상세조회 API(detailIntro2)를 한 번 더 호출해서
    //       PlaceReality.priceText/parkingInfo를 채우는 로직을 추가할 것.
  }

  // 블랙리스트 필터 도입(2026-08-14) 이전에 이미 저장되어 있던 프랜차이즈/체인점을 정리한다.
  // 관리자가 수동으로 한 번 호출하는 일회성 정리용 메서드(AdminController에서 호출).
  // place_styles/course_items 등 자식 테이블이 이 장소를 참조하고 있으면 FK 제약(ORA-02292)으로
  // 삭제가 실패하는데, 그런 장소는 이미 코스에 담기는 등 실사용 데이터가 있다는 뜻이라 억지로 지우지
  // 않고 건너뛴다. 한 건씩 개별 트랜잭션으로 처리해서, 하나가 막혀도 나머지 삭제는 계속 진행된다.
  @Override
  public Map<String, Integer> cleanupBlacklistedPlaces() {
    int deleted = 0;
    int skipped = 0;
    for (String keyword : BLACKLISTED_NAME_KEYWORDS) {
      for (Place place : placeRepository.findByNameContaining(keyword)) {
        try {
          // place_styles는 매 장소마다 1:1로 자동 생성돼 있어서(성향점수 중립값), 이건 실사용
          // 데이터가 아니라 부산물이므로 먼저 지워도 안전하다.
          placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
          placeRepository.delete(place);
          placeRepository.flush();
          deleted++;
        } catch (DataIntegrityViolationException e) {
          skipped++;
        }
      }
    }

    // 백화점 내 개별 브랜드 매장(2026-08-19 추가된 규칙)도 같은 방식으로 정리 - 키워드 하나로는
    // 못 걸러서(예: "백화점"만 블랙리스트하면 백화점 자체까지 다 지워짐) 카테고리가 "쇼핑"인
    // 것만 전부 훑어서 isBrandInsideMall로 판단한다.
    for (Place place : placeRepository.findByCategory("쇼핑", org.springframework.data.domain.Pageable.unpaged())) {
      if (!isBrandInsideMall(place.getName())) {
        continue;
      }
      try {
        placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
        placeRepository.delete(place);
        placeRepository.flush();
        deleted++;
      } catch (DataIntegrityViolationException e) {
        skipped++;
      }
    }

    // 쇼핑에 잘못 섞여 들어온 병의원 정리(2026-08-19) - isMedicalFacility 참고.
    for (Place place : placeRepository.findByCategory("쇼핑", org.springframework.data.domain.Pageable.unpaged())) {
      if (!isMedicalFacility(place.getName())) {
        continue;
      }
      try {
        placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
        placeRepository.delete(place);
        placeRepository.flush();
        deleted++;
      } catch (DataIntegrityViolationException e) {
        skipped++;
      }
    }

    // 쇼핑 중 안경점/담배·전자담배 판매점 정리(2026-08-19) - isExcludedShoppingKeyword 참고.
    for (Place place : placeRepository.findByCategory("쇼핑", org.springframework.data.domain.Pageable.unpaged())) {
      if (!isExcludedShoppingKeyword(place.getName())) {
        continue;
      }
      try {
        placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
        placeRepository.delete(place);
        placeRepository.flush();
        deleted++;
      } catch (DataIntegrityViolationException e) {
        skipped++;
      }
    }

    // 이름 반복 기반 프랜차이즈 정리(2026-08-19) - DB에 이미 쌓인 쇼핑 데이터를 대상으로, syncPlaces와
    // 같은 기준(첫 단어 5회 이상 반복, 백화점/아울렛 제외)으로 브랜드를 찾아서 지운다.
    List<Place> shoppingPlaces = placeRepository.findByCategory("쇼핑", org.springframework.data.domain.Pageable.unpaged())
        .getContent();
    Map<String, Long> brandCounts = shoppingPlaces.stream()
        .filter(place -> place.getName() != null && !place.getName().contains("백화점") && !place.getName().contains("아울렛"))
        .collect(java.util.stream.Collectors.groupingBy(place -> brandKey(place.getName()), java.util.stream.Collectors.counting()));
    java.util.Set<String> franchiseBrandKeys = brandCounts.entrySet().stream()
        .filter(e -> e.getValue() >= SHOPPING_FRANCHISE_THRESHOLD)
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toSet());

    for (Place place : shoppingPlaces) {
      if (!franchiseBrandKeys.contains(brandKey(place.getName()))) {
        continue;
      }
      try {
        placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
        placeRepository.delete(place);
        placeRepository.flush();
        deleted++;
      } catch (DataIntegrityViolationException e) {
        skipped++;
      }
    }

    // 전통시장 정리(2026-08-19, 사용자 요청 - 백화점과 같은 문제) - "전통시장"으로 분류된 곳 중
    // ①이름 첫 단어에 "시장"이 없으면(예: "로우로우 광장시장점") 시장 자체가 아니라 그 안의 브랜드
    // 매장이라 제외하고, ②같은 시장의 하위 구역/상가(예: "광장시장 한복매장")는 순수 시장 이름
    // ("광장시장") 항목이 이미 있으면 그쪽만 남기고 나머지는 제외한다. "역전시장(순천)"처럼 지역이
    // 이름에 괄호로 붙어있는 경우는 첫 단어 자체가 달라져서(공백 없이 붙음) 서로 다른 시장으로
    // 안전하게 구분된다 - 진짜 다른 지역 시장을 잘못 합치는 걸 막아준다.
    List<Place> marketPlaces = placeRepository.findByCategory("쇼핑", org.springframework.data.domain.Pageable.unpaged())
        .getContent().stream()
        .filter(place -> place.getPlaceCategory() != null && "전통시장".equals(place.getPlaceCategory().getSubCategory()))
        .toList();

    List<Place> brandAtMarket = marketPlaces.stream()
        .filter(place -> place.getName() != null && !brandKey(place.getName()).contains("시장"))
        .toList();
    for (Place place : brandAtMarket) {
      try {
        placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
        placeRepository.delete(place);
        placeRepository.flush();
        deleted++;
      } catch (DataIntegrityViolationException e) {
        skipped++;
      }
    }

    Map<String, List<Place>> byMarketKey = marketPlaces.stream()
        .filter(place -> !brandAtMarket.contains(place))
        .collect(java.util.stream.Collectors.groupingBy(place -> brandKey(place.getName())));
    for (Map.Entry<String, List<Place>> group : byMarketKey.entrySet()) {
      String marketKey = group.getKey();
      boolean hasBareEntry = group.getValue().stream().anyMatch(place -> marketKey.equals(place.getName()));
      if (!hasBareEntry) {
        continue; // 순수 시장 이름 항목이 없으면 뭘 대표로 남길지 판단할 근거가 없어서 그대로 둔다.
      }
      for (Place place : group.getValue()) {
        if (marketKey.equals(place.getName()) || !place.getName().startsWith(marketKey + " ")) {
          continue; // 대표 항목이거나, 같은 첫 단어를 우연히 공유할 뿐 하위 구역이 아닌 경우는 건드리지 않음
        }
        try {
          placeStyleRepository.findByPlace_Id(place.getId()).ifPresent(placeStyleRepository::delete);
          placeRepository.delete(place);
          placeRepository.flush();
          deleted++;
        } catch (DataIntegrityViolationException e) {
          skipped++;
        }
      }
    }

    log.info("블랙리스트 장소 정리 완료 - {}건 삭제, {}건 연관 데이터로 건너뜀", deleted, skipped);
    return Map.of("deleted", deleted, "skipped", skipped);
  }

  // TourAPI가 한 번에 내려주는 최대 건수(그 이상 요청하면 페이지가 잘림)
  private static final int TOURAPI_PAGE_SIZE = 100;

  // 무한 루프 방지용 안전장치. 카테고리당 최대 100 * 200 = 20000건까지 수집.
  // (실측 결과 맛집 13,515건/관광지 12,644건/쇼핑 12,242건이 가장 많아 여유 있게 잡음 - 200페이지면 충분)
  private static final int TOURAPI_MAX_PAGES = 200;

  /**
   * contentTypeId 하나에 대해 전국 목록을 전부 받아옴. areaBasedList2는 한 번에 최대 100건만
   * 내려주기 때문에(numOfRows 상한), pageNo를 1씩 늘려가며 totalCount만큼 다 모을 때까지 반복 호출한다.
   * (예전엔 pageNo=1 고정이라 카테고리당 최대 100건만 저장되고 나머지는 전부 유실되던 버그가 있었음 -
   *  예: 맛집은 전국 13,515건인데 100건만 저장되고 있었음)
   */
  private List<TourApiPlaceDto> fetchPlaces(String contentTypeId) {
    List<TourApiPlaceDto> all = new ArrayList<>();

    for (int page = 1; page <= TOURAPI_MAX_PAGES; page++) {
      // serviceKey의 +,/,=를 URLEncoder로 직접 인코딩(UriComponentsBuilder.encode()는 +를 인코딩 안 해서
      // 공공데이터포털 서버가 공백으로 오해석하는 문제가 있었음).
      String encodedServiceKey = java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8);
      String url = UriComponentsBuilder.fromUriString(LIST_URL)
          .queryParam("serviceKey", encodedServiceKey)
          .queryParam("MobileOS", "ETC")
          .queryParam("MobileApp", "DateManager")
          .queryParam("_type", "json")
          .queryParam("numOfRows", TOURAPI_PAGE_SIZE)
          .queryParam("pageNo", page)
          .queryParam("contentTypeId", contentTypeId)
          .build(true) // true: 위 값들을 이미 인코딩된 것으로 취급 (serviceKey 이중 인코딩 방지)
          .toUriString();

      List<TourApiPlaceDto> pageItems;
      try {
        // 주의: getForObject(String, ...)는 문자열을 URI 템플릿으로 보고 내부적으로 다시 한 번
        // 인코딩한다 - 이미 인코딩해둔 serviceKey가 %2F -> %252F 처럼 이중 인코딩되어 완전히 다른
        // 값이 전송되는 버그가 있었다("등록되지 않은 서비스키" 에러의 진짜 원인). URI로 직접 넘기면
        // RestTemplate이 재인코딩 없이 그대로 보낸다.
        JsonNode root = restTemplate.getForObject(java.net.URI.create(url), JsonNode.class);
        if (root == null) break;

        JsonNode items = root.path("response").path("body").path("items").path("item");
        pageItems = new ArrayList<>();
        for (JsonNode item : items) {
          String contentId = item.path("contentid").asText("");
          String title = item.path("title").asText("");
          if (contentId.isBlank() || title.isBlank()) continue; // 필수 정보 없는 항목은 건너뜀

          pageItems.add(new TourApiPlaceDto(
              contentId,
              title,
              item.path("addr1").asText(""),
              item.path("addr2").asText(""),
              item.path("mapx").asText(""),
              item.path("mapy").asText(""),
              item.path("firstimage").asText(""),
              item.path("firstimage2").asText("")
          ));
        }
      } catch (Exception e) {
        log.error("TourAPI 호출 실패 (contentTypeId={}, page={})", contentTypeId, page, e);
        break;
      }

      if (pageItems.isEmpty()) break;
      all.addAll(pageItems);

      if (pageItems.size() < TOURAPI_PAGE_SIZE) break; // 마지막 페이지까지 다 읽음
    }

    return all;
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

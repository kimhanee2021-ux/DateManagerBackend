package org.ict.datemanagerbackend.domain.place.service;

import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.dto.MuseumDto;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 공공데이터포털의 "전국박물관미술관정보표준데이터"(tn_pubr_public_museum_artgr_info_api)에서
 * 박물관/미술관 데이터를 받아와 places 테이블에 채워 넣는 서비스.
 *
 * <p>이 API는 TourApiSyncService가 쓰는 KorService2와 같은 공공데이터포털 계정 인증키를 그대로
 * 쓰지만(그래서 application.yaml의 tourapi.service-key를 공유해서 씀), 데이터셋마다 별도로
 * "활용신청" 승인을 받아야 호출이 가능하다(같은 키라도 신청 안 한 API는 404/403 에러가 난다).
 *
 * <p><b>KOPIS/TourAPI와 다른 점 — 고유 ID가 없다</b><br>
 * KOPIS는 mt20id, TourAPI는 contentid라는 고유 식별자를 내려주는데, 이 표준데이터 API는 그런 ID가
 * 아예 없다(시설명/주소/위경도 등 "값" 데이터만 준다). 그래서 upsert(이미 있으면 갱신, 없으면 새로 만듦)를
 * 하려면 우리가 직접 "이 장소를 유일하게 식별할 값"을 만들어야 하는데, 여기선 (시설명 + 도로명주소)를
 * 합친 문자열을 MD5 해시로 변환해서 external_id로 사용한다 — 이름과 주소가 같으면 항상 같은 해시값이
 * 나오므로, 같은 시설이 매번 같은 ID로 매칭되어 중복 저장되지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MuseumSyncServiceImpl implements MuseumSyncService {

  private static final String LIST_URL = "https://api.data.go.kr/openapi/tn_pubr_public_museum_artgr_info_api";
  private static final String EXTERNAL_SOURCE = "MUSEUM_STD";
  private static final String CATEGORY = "박물관/미술관";

  private static final int PAGE_SIZE = 500;
  private static final int MAX_PAGES = 10; // 500 * 10 = 최대 5000건까지 (현재 전국 1074건 확인됨, 여유있게 설정)

  // 시설명(정확히 일치) -> 대표사진 URL(위키미디어, 2026-08-28). 이 API 자체는 사진 필드가 없어서
  // 471/473건이 무사진이었다(사용자 지적) - 전체 미사진 시설명을 위키백과에 일괄 조회해 위키 문서가
  // 있는(=유명한) 곳만 채운다. 지역 소규모 박물관 대부분은 위키 문서가 없어 이 방식으로는 못 채우고
  // 비워둔다 - SportsSyncServiceImpl의 STADIUM_IMAGES와 같은 접근.
  private static final java.util.Map<String, String> NAME_IMAGES = java.util.Map.ofEntries(
      java.util.Map.entry("강릉원주대학교박물관", "https://upload.wikimedia.org/wikipedia/commons/7/7a/%EA%B5%AD%EB%A6%BD%EA%B0%95%EB%A6%89%EC%9B%90%EC%A3%BC%EB%8C%80%ED%95%99%EA%B5%901.jpg"),
      java.util.Map.entry("경주솔거미술관", "https://upload.wikimedia.org/wikipedia/commons/f/f6/Solgeo_Art_Museum%2C_Gyeongju_on_December_25th%2C_2018.jpg"),
      java.util.Map.entry("국립민속박물관", "https://upload.wikimedia.org/wikipedia/commons/d/da/Korea-Seoul-National.folk.museum-01.JPG"),
      java.util.Map.entry("국회박물관", "https://upload.wikimedia.org/wikipedia/commons/c/c7/%EA%B5%AD%ED%9A%8C%EB%B0%95%EB%AC%BC%EA%B4%80.jpg"),
      java.util.Map.entry("기당미술관", "https://upload.wikimedia.org/wikipedia/commons/f/fe/%EA%B8%B0%EB%8B%B9%EB%AF%B8%EC%88%A0%EA%B4%80_240727.jpg"),
      java.util.Map.entry("디자인코리아뮤지엄", "https://upload.wikimedia.org/wikipedia/commons/b/b6/Design_Korea_Museum_%28South_Korea%29.jpg"),
      java.util.Map.entry("말박물관", "https://upload.wikimedia.org/wikipedia/ko/0/00/%EB%A7%88%EC%82%AC%EB%B0%95%EB%AC%BC%EA%B4%80_%EC%A0%84%EA%B2%BD.JPG"),
      java.util.Map.entry("문경석탄박물관", "https://upload.wikimedia.org/wikipedia/commons/c/c5/MG-MGCM-En.jpg"),
      java.util.Map.entry("쇳대박물관", "https://upload.wikimedia.org/wikipedia/commons/2/22/Lock_Museum.jpg"),
      java.util.Map.entry("수도국산달동네박물관", "https://upload.wikimedia.org/wikipedia/ko/e/e5/%EC%88%98%EB%8F%84%EA%B5%AD%EC%82%B0%EB%B0%95%EB%AC%BC%EA%B4%80.jpg"),
      java.util.Map.entry("애니메이션박물관", "https://upload.wikimedia.org/wikipedia/commons/3/3d/Animation_Museum_of_South_Korea_02.jpg"),
      java.util.Map.entry("양구군립박수근미술관", "https://upload.wikimedia.org/wikipedia/commons/c/c9/%EB%B0%95%EC%88%98%EA%B7%BC%EB%AF%B8%EC%88%A0%EA%B4%80-tourgo.jpg"),
      java.util.Map.entry("에코랜드", "https://upload.wikimedia.org/wikipedia/commons/a/a2/%EC%A0%9C%EC%A3%BC%EB%8F%84_%EC%97%90%EC%BD%94%EB%9E%9C%EB%93%9C.jpeg"),
      java.util.Map.entry("오죽헌시립박물관", "https://upload.wikimedia.org/wikipedia/commons/1/16/%EA%B0%95%EB%A6%89%EC%8B%9C%EB%A6%BD%EB%B0%95%EB%AC%BC%EA%B4%80.jpg"),
      java.util.Map.entry("원주역사박물관", "https://upload.wikimedia.org/wikipedia/commons/0/0e/Wonju_Museum_of_History.jpg"),
      java.util.Map.entry("의병박물관", "https://upload.wikimedia.org/wikipedia/ko/7/7e/%EC%9D%98%EB%B3%91%EB%B0%95%EB%AC%BC%EA%B4%80.jpg"),
      java.util.Map.entry("전북특별자치도립미술관", "https://upload.wikimedia.org/wikipedia/commons/b/b5/Jeonbuk_Museum_of_Art%2C_in_Wanju%2C_North_Jeolla_Province%2C_South_Korea_07.jpg"),
      java.util.Map.entry("전라남도농업박물관", "https://upload.wikimedia.org/wikipedia/commons/b/b0/%EC%A0%84%EB%9D%BC%EB%82%A8%EB%8F%84%EB%86%8D%EC%97%85%EB%B0%95%EB%AC%BC%EA%B4%80.jpg"),
      java.util.Map.entry("조선대학교미술관", "https://upload.wikimedia.org/wikipedia/ko/7/72/Chosun_University_College_of_Art.jpg"),
      java.util.Map.entry("부산시립박물관", "https://upload.wikimedia.org/wikipedia/commons/a/af/Busan_museum.JPG"),
      java.util.Map.entry("삼척시립박물관", "https://upload.wikimedia.org/wikipedia/commons/8/82/Samcheok_Municipal_Museum.jpg"),
      java.util.Map.entry("삼성미술관 Leeum", "https://upload.wikimedia.org/wikipedia/commons/c/c4/Leeum%2C_Samsung_Museum_of_Art.jpg"),
      java.util.Map.entry("서울역사박물관", "https://upload.wikimedia.org/wikipedia/commons/e/ed/%EC%84%9C%EC%9A%B8%EC%97%AD%EC%82%AC%EB%B0%95%EB%AC%BC%EA%B4%80_%EB%A1%9C%EA%B3%A0.jpg"),
      java.util.Map.entry("철새박물관", "https://upload.wikimedia.org/wikipedia/commons/5/52/Seosan_Bird_Land.jpg"),
      java.util.Map.entry("한국만화박물관", "https://upload.wikimedia.org/wikipedia/commons/3/30/Korea_Manhwa_Museum.JPG"),
      java.util.Map.entry("한국이민사박물관", "https://upload.wikimedia.org/wikipedia/commons/9/96/Museum_of_Korea_Emigration_History_in_2016.JPG"),
      java.util.Map.entry("한국조폐공사 화폐박물관", "https://upload.wikimedia.org/wikipedia/commons/e/e2/%ED%99%94%ED%8F%90%EB%B0%95%EB%AC%BC%EA%B4%80_Currency_Museum.jpg"),
      java.util.Map.entry("한국은행 화폐금융박물관", "https://upload.wikimedia.org/wikipedia/commons/8/8e/Bank_of_Korea_20070103.jpg"),
      java.util.Map.entry("한무숙문학관", "https://upload.wikimedia.org/wikipedia/commons/d/d9/The_Front_Gate_of_Han_Musuk_Museum.jpg")
      // "화정박물관"은 위키 조회 결과가 국립중앙박물관 사진으로 잘못 연결돼(오매칭 확인, 2026-08-28)
      // 신뢰할 수 없어 제외함 - 다른 소스로 확인 후 추가할 것.
  );

  private final PlaceRepository placeRepository;
  private final PlaceDedupService placeDedupService;
  private final RestTemplate restTemplate = new RestTemplate();

  // TourApiSyncService와 같은 공공데이터포털 계정 키를 공유해서 사용한다(데이터셋별 활용신청은 별도로 필요).
  @Value("${tourapi.service-key}")
  private String serviceKey;

  /**
   * 매일 새벽 4시 30분에 자동 실행됨 (KOPIS 3시, TourAPI 4시와 겹치지 않도록 30분 뒤로 배치).
   */
  @Scheduled(cron = "0 30 4 * * *")
  @Override
  public void syncMuseums() {
    int created = 0;
    int updated = 0;

    for (MuseumDto m : fetchAll()) {
      String address = (m.rdnmadr() != null && !m.rdnmadr().isBlank()) ? m.rdnmadr() : m.lnmadr();
      if (m.fcltyNm() == null || m.fcltyNm().isBlank() || address == null || address.isBlank()) {
        continue; // 이름/주소가 없으면 식별용 해시를 만들 수 없어 건너뜀
      }

      String externalId = hashKey(m.fcltyNm() + "|" + address);
      Double lat = parseCoordinate(m.latitude());
      Double lng = parseCoordinate(m.longitude());

      Optional<Place> existing = placeRepository.findByExternalSourceAndExternalId(EXTERNAL_SOURCE, externalId);
      if (existing.isPresent()) {
        Place place = existing.get();
        place.setName(m.fcltyNm());
        place.setCategory(CATEGORY);
        place.setAddress(address);
        place.setLatitude(lat);
        place.setLongitude(lng);
        if (place.getImageUrl() == null && NAME_IMAGES.containsKey(m.fcltyNm())) {
          place.setImageUrl(NAME_IMAGES.get(m.fcltyNm()));
        }
        placeRepository.save(place);
        updated++;
        continue;
      }

      // 박물관/미술관은 TourAPI의 "문화시설" 카테고리와 겹칠 수 있어(예: 국립중앙박물관이 양쪽에 다 있을
      // 수 있음) 이름+좌표로 다른 소스에 이미 있는지 한 번 더 확인한다.
      Optional<Place> duplicate = placeDedupService.findDuplicate(m.fcltyNm(), lat, lng);
      if (duplicate.isPresent()) {
        updated++;
      } else {
        Place place = Place.builder()
            .name(m.fcltyNm())
            .category(CATEGORY)
            .address(address)
            .latitude(lat)
            .longitude(lng)
            .imageUrl(NAME_IMAGES.get(m.fcltyNm()))
            .externalSource(EXTERNAL_SOURCE)
            .externalId(externalId)
            .build();
        placeRepository.save(place);
        created++;
      }
    }

    log.info("박물관/미술관 표준데이터 동기화 완료 - 신규 {}건, 갱신 {}건", created, updated);
  }

  /** 전체 페이지를 순회하며 박물관/미술관 목록을 모아온다 */
  private List<MuseumDto> fetchAll() {
    List<MuseumDto> all = new ArrayList<>();

    for (int page = 1; page <= MAX_PAGES; page++) {
      // serviceKey의 +,/,= 같은 특수문자를 URLEncoder로 직접 인코딩(UriComponentsBuilder.encode()는
      // +를 인코딩하지 않아 공공데이터포털 서버가 공백으로 오해석하는 문제가 있었음) 후 build(true)로
      // 이중 인코딩을 막는다.
      String url = UriComponentsBuilder.fromUriString(LIST_URL)
          .queryParam("serviceKey", java.net.URLEncoder.encode(serviceKey, java.nio.charset.StandardCharsets.UTF_8))
          .queryParam("pageNo", page)
          .queryParam("numOfRows", PAGE_SIZE)
          .queryParam("type", "json")
          .build(true)
          .toUriString();

      try {
        // getForObject(String, ...)는 이미 인코딩된 URL을 다시 인코딩해버리는(이중 인코딩) 문제가 있어
        // URI로 직접 넘긴다 (TourAPI에서 이 문제로 실제 인증 실패를 겪었음).
        JsonNode root = restTemplate.getForObject(java.net.URI.create(url), JsonNode.class);
        if (root == null) break;

        JsonNode items = root.path("body").path("items").path("item");
        if (!items.isArray() || items.isEmpty()) break;

        for (JsonNode item : items) {
          all.add(new MuseumDto(
              item.path("fcltyNm").asText(""),
              item.path("rdnmadr").asText(""),
              item.path("lnmadr").asText(""),
              item.path("latitude").asText(""),
              item.path("longitude").asText("")
          ));
        }

        if (items.size() < PAGE_SIZE) break; // 마지막 페이지
      } catch (Exception e) {
        log.error("박물관/미술관 표준데이터 호출 실패 (page={})", page, e);
        break;
      }
    }

    return all;
  }

  /** (시설명+주소) 문자열을 MD5 해시(32자 16진수)로 변환 - 고유 ID가 없는 데이터의 대체 식별자용 */
  private String hashKey(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      // MD5는 JVM에 항상 존재하는 표준 알고리즘이라 사실상 발생하지 않는 예외 - 방어적으로만 처리
      return String.valueOf(raw.hashCode());
    }
  }

  private Double parseCoordinate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

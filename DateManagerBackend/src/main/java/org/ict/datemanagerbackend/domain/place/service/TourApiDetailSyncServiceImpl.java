package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceAmenity;
import org.ict.datemanagerbackend.domain.place.entity.PlaceImage;
import org.ict.datemanagerbackend.domain.place.entity.PlaceReality;
import org.ict.datemanagerbackend.domain.place.repository.PlaceAmenityRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceImageRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRealityRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * TourAPI 상세정보(detailIntro2)·사진갤러리(detailImage2) 동기화(2026-08-26 추가).
 *
 * <p>areaBasedList2(TourApiSyncServiceImpl)는 이름/주소/좌표/대표사진 1장만 내려줘서, 운영시간·
 * 휴무일·입장료·주차·편의시설·추가 사진은 지금까지 전부 비어있었다. 이 서비스가 그 공백을 채운다.
 *
 * <p>주의: TourAPI는 콘텐츠타입ID(contentTypeId)마다 같은 의미의 필드도 이름이 다르다(예: 이용시간이
 * 문화시설은 usetimeculture, 음식점은 opentimefood). 아래 FIELD_MAP은 문화시설(14)만 실제 호출로
 * 검증했고(2026-08-26), 나머지 타입은 TourAPI 공식 문서의 일반적인 명명 규칙을 따른 것이라 실제 값이
 * 안 들어오면 필드명이 다를 수 있다 - 그래도 크래시 없이 조용히 빈 값으로 남을 뿐이니 안전하다.
 *
 * <p>개발계정은 하루 1,000건 한도라 limit으로 배치 크기를 조절하며 여러 번 나눠 호출한다.
 * Place.detailSynced를 커서로 써서 이미 시도한 장소는 자동으로 건너뛴다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TourApiDetailSyncServiceImpl implements TourApiDetailSyncService {

  private static final String DETAIL_INTRO_URL = "https://apis.data.go.kr/B551011/KorService2/detailIntro2";
  private static final String DETAIL_IMAGE_URL = "https://apis.data.go.kr/B551011/KorService2/detailImage2";

  // 우리 Place.category(한글) -> TourAPI contentTypeId. TourApiSyncServiceImpl.CATEGORY_BY_CONTENT_TYPE의
  // 역방향 - 상세조회 호출 시 contentTypeId가 필수 파라미터라 다시 필요하다.
  private static final Map<String, String> CONTENT_TYPE_BY_CATEGORY = Map.of(
      "관광지", "12",
      "문화시설", "14",
      "액티비티", "28",
      "숙박", "32",
      "쇼핑", "38",
      "맛집", "39"
  );

  private record FieldMap(String useTime, String restDate, String fee, String parking,
                           String chkBaby, String chkPet, String chkCard, String menu) {
    FieldMap(String useTime, String restDate, String fee, String parking,
             String chkBaby, String chkPet, String chkCard) {
      this(useTime, restDate, fee, parking, chkBaby, chkPet, chkCard, null);
    }
  }

  // 콘텐츠타입별 detailIntro2 필드명 매핑. 2026-08-31에 전 콘텐츠타입(12/28/32/38/39) 실제 호출로
  // 검증함 - 관광지(12)/숙박(32)/맛집(39)은 응답에 요금 관련 필드가 아예 없어서 fee가 null인 게
  // 정상(코드 누락이 아니라 TourAPI 자체의 데이터 한계). 숙박은 룸타입별로 가격이 갈려서 원래
  // 단일 가격 필드를 안 준다.
  private static final Map<String, FieldMap> FIELD_MAP_BY_CONTENT_TYPE = Map.of(
      "12", new FieldMap("usetime", "restdate", null, "parking", "chkbabycarriage", "chkpet", "chkcreditcard"),
      "14", new FieldMap("usetimeculture", "restdateculture", "usefee", "parkingculture",
          "chkbabycarriageculture", "chkpetculture", "chkcreditcardculture"),
      "28", new FieldMap("usetimeleports", "restdateleports", "usefeeleports", "parkingleports",
          "chkbabycarriageleports", "chkpetleports", "chkcreditcardleports"),
      "32", new FieldMap("checkintime", null, null, "parkinglodging", null, "chkpetlodging", null),
      // saleitemcost(2026-08-31 추가) - 실제 응답 검증 완료(값이 비어있는 경우도 많지만 필드 자체는 존재).
      "38", new FieldMap("opentime", "restdateshopping", "saleitemcost", "parkingshopping", null, null, "chkcreditcardshopping"),
      // firstmenu(2026-08-31 추가) - 대표메뉴. treatmenu(취급메뉴 목록)도 응답에 있지만 카드에 노출할
      // 짧은 한 줄이면 충분해서 firstmenu만 저장한다.
      "39", new FieldMap("opentimefood", "restdatefood", null, "parkingfood", null, null, "chkcreditcardfood", "firstmenu")
  );

  private final PlaceRepository placeRepository;
  private final PlaceRealityRepository placeRealityRepository;
  private final PlaceAmenityRepository placeAmenityRepository;
  private final PlaceImageRepository placeImageRepository;
  private final RestTemplate restTemplate = buildRestTemplate();

  @Value("${tourapi.service-key}")
  private String serviceKey;

  private static RestTemplate buildRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    return new RestTemplate(factory);
  }

  // 개발계정 일일 호출 제한(1,000건) 안에서 매일 자동으로 나눠 돌린다. TourApiSyncService(4시)/
  // MuseumSyncService(4시30분)와 안 겹치게 5시15분으로 배치(2026-08-26).
  @Scheduled(cron = "0 15 5 * * *")
  public void scheduledSync() {
    DetailSyncResult result = syncDetails(900); // 1,000건 한도에 여유를 좀 남겨둠
    log.info("TourAPI 상세정보 자동 동기화 완료 - 시도 {}건, 채움 {}건, 정보없음 {}건, 실패 {}건",
        result.attempted(), result.filled(), result.noData(), result.failed());
  }

  @Override
  public DetailSyncResult syncDetails(int limit) {
    List<Place> targets = pickTargetsRoundRobin(limit);

    int attempted = 0;
    int filled = 0;
    int noData = 0;
    int failed = 0;

    for (Place place : targets) {
      String contentTypeId = CONTENT_TYPE_BY_CATEGORY.get(place.getCategory());
      if (contentTypeId == null || place.getExternalId() == null) {
        // 매핑 안 되는 카테고리(우리 자체 분류일 뿐 TourAPI 콘텐츠타입이 아닌 경우)는 다시 시도할
        // 필요가 없으니 바로 "완료"로 표시해 커서에서 빠지게 한다.
        place.setDetailSynced(true);
        placeRepository.save(place);
        continue;
      }

      attempted++;
      try {
        boolean gotIntro = syncIntro(place, contentTypeId);
        boolean gotImages = syncImages(place, contentTypeId);
        place.setDetailSynced(true);
        placeRepository.save(place);
        if (gotIntro || gotImages) {
          filled++;
        } else {
          noData++;
        }
      } catch (Exception e) {
        log.warn("TourAPI 상세정보 조회 실패 (placeId={}, contentId={})", place.getId(), place.getExternalId(), e);
        failed++;
        // 실패해도 detailSynced를 true로 남겨서 다음 배치에서 같은 장소로 예산을 또 낭비하지 않는다 -
        // 진짜 일시적 오류였던 소수는 놓치더라도, 전체 배치 진행이 훨씬 중요하다고 판단.
        place.setDetailSynced(true);
        placeRepository.save(place);
      }
      sleepBriefly();
    }

    return new DetailSyncResult(attempted, filled, noData, failed);
  }

  // id 단일 커서로만 돌면 id가 낮은 카테고리(맛집) 하나만 몇 주째 다 채우고 나머지 카테고리
  // (관광지/숙박 등)는 하나도 못 건드리는 문제가 있었다(2026-08-27 발견 - PlaceReality가
  // 100% 맛집에만 몰려있던 원인). 이번 배치 한도(limit)를 남은 카테고리 수만큼 균등하게 나눠
  // 카테고리마다 오래된 것부터 가져오고, 나머지(나눗셈 몫으로 안 떨어지는 만큼)는 앞쪽
  // 카테고리부터 하나씩 더 배정한다.
  private List<Place> pickTargetsRoundRobin(int limit) {
    List<String> categories = placeRepository.findCategoriesPendingTourApiDetailSync();
    if (categories.isEmpty()) return List.of();

    int perCategory = limit / categories.size();
    int remainder = limit % categories.size();

    List<Place> targets = new java.util.ArrayList<>();
    for (int i = 0; i < categories.size(); i++) {
      int take = perCategory + (i < remainder ? 1 : 0);
      if (take <= 0) continue;
      targets.addAll(placeRepository.findTourApiPlacesPendingDetailSyncByCategory(categories.get(i), PageRequest.of(0, take)));
    }
    return targets;
  }

  private boolean syncIntro(Place place, String contentTypeId) {
    FieldMap fields = FIELD_MAP_BY_CONTENT_TYPE.get(contentTypeId);
    if (fields == null) return false;

    JsonNode item = callTourApi(DETAIL_INTRO_URL, Map.of(
        "contentId", place.getExternalId(),
        "contentTypeId", contentTypeId
    ));
    if (item == null) return false;

    String useTime = textOrNull(item, fields.useTime());
    String restDate = textOrNull(item, fields.restDate());
    String fee = textOrNull(item, fields.fee());
    String parking = textOrNull(item, fields.parking());
    String menu = textOrNull(item, fields.menu());

    boolean hasAny = useTime != null || restDate != null || fee != null || parking != null || menu != null;
    if (hasAny) {
      PlaceReality reality = placeRealityRepository.findByPlace_Id(place.getId())
          .orElseGet(() -> PlaceReality.builder().place(place).build());
      if (useTime != null) reality.setUseTime(useTime);
      if (restDate != null) reality.setRestDate(restDate);
      if (fee != null) reality.setPriceText(truncate(fee, 50));
      if (parking != null) reality.setParkingInfo(truncate(parking, 100));
      if (menu != null) reality.setMenuInfo(truncate(menu, 300));
      placeRealityRepository.save(reality);
    }

    addAmenityIfPositive(place, item, fields.chkBaby(), "BABY_CARRIAGE");
    addAmenityIfPositive(place, item, fields.chkPet(), "PET");
    addAmenityIfPositive(place, item, fields.chkCard(), "CARD_PAYMENT");

    return hasAny;
  }

  // chk* 필드는 자유 텍스트라("가능"/"불가"/"없음" 등) 단순 Y/N이 아니다 - "불가"/"없음"/공백이
  // 아니면 긍정으로 간주해 태그를 붙인다. 이미 같은 태그가 있으면 중복 저장하지 않는다.
  private void addAmenityIfPositive(Place place, JsonNode item, String fieldName, String amenityTag) {
    if (fieldName == null) return;
    String raw = textOrNull(item, fieldName);
    if (raw == null) return;
    if (raw.contains("불가") || raw.contains("없음") || raw.contains("No")) return;
    if (placeAmenityRepository.existsByPlace_IdAndAmenityTag(place.getId(), amenityTag)) return;
    placeAmenityRepository.save(PlaceAmenity.builder().place(place).amenityTag(amenityTag).build());
  }

  private boolean syncImages(Place place, String contentTypeId) {
    // subImageYN 파라미터를 보내면 "INVALID_REQUEST_PARAMETER_ERROR"가 나서(2026-08-26 실측) 뺐다.
    JsonNode root = callTourApiRaw(DETAIL_IMAGE_URL, Map.of(
        "contentId", place.getExternalId(),
        "imageYN", "Y"
    ));
    if (root == null) return false;

    JsonNode items = root.path("response").path("body").path("items").path("item");
    if (!items.isArray() || items.isEmpty()) return false;

    // 재동기화 시 중복 누적을 막기 위해 기존 갤러리를 지우고 새로 채운다.
    placeImageRepository.deleteByPlace_Id(place.getId());

    int order = 0;
    boolean any = false;
    for (JsonNode img : items) {
      String url = img.path("originimgurl").asText("");
      if (url.isBlank()) continue;
      placeImageRepository.save(PlaceImage.builder().place(place).imageUrl(url).sortOrder(order++).build());
      any = true;
    }
    return any;
  }

  private String textOrNull(JsonNode item, String fieldName) {
    if (fieldName == null) return null;
    String value = item.path(fieldName).asText("");
    return value.isBlank() ? null : value.trim();
  }

  private String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  // detailIntro2 응답에서 items>item(보통 단일 객체)을 꺼낸다.
  private JsonNode callTourApi(String url, Map<String, String> extraParams) {
    JsonNode root = callTourApiRaw(url, extraParams);
    if (root == null) return null;
    JsonNode item = root.path("response").path("body").path("items").path("item");
    if (item.isArray()) {
      return item.isEmpty() ? null : item.get(0);
    }
    return item.isMissingNode() ? null : item;
  }

  private JsonNode callTourApiRaw(String url, Map<String, String> extraParams) {
    String encodedServiceKey = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url)
        .queryParam("serviceKey", encodedServiceKey)
        .queryParam("MobileOS", "ETC")
        .queryParam("MobileApp", "DateManager")
        .queryParam("_type", "json");
    extraParams.forEach(builder::queryParam);
    String fullUrl = builder.build(true).toUriString();

    try {
      return restTemplate.getForObject(URI.create(fullUrl), JsonNode.class);
    } catch (Exception e) {
      log.warn("TourAPI 상세 호출 실패 (url={})", url, e);
      return null;
    }
  }

  private void sleepBriefly() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

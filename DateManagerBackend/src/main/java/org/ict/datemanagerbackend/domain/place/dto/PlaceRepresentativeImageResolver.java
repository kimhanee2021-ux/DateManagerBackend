package org.ict.datemanagerbackend.domain.place.dto;

import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;

import java.util.List;
import java.util.Map;

// 실제 사진(Place.imageUrl)이 없는 카드에 보여줄 대표이미지를 고르는 로직(2026-08-31).
// 대부분의 세부분류는 PlaceCategory.representativeImageUrl 1장을 그대로 쓰지만, 물량이 아주 많은
// 세부분류(모텔/호텔/게스트하우스, 각 7천~1만3천건)는 같은 화면에 카드가 수십 개씩 뜨는데 전부
// 똑같은 사진이면 단조로워 보인다는 피드백을 받아, 이 3개만 후보 3장을 두고 장소 ID 기준으로
// 고정 배정(rotation)한다 - 같은 장소는 새로고침해도 항상 같은 사진(깜빡임 없음), 다른 장소는
// 다른 사진(다양성). 나머지 세부분류(펜션/리조트/한옥 등)는 물량이 적어 1장으로도 충분하다고 판단해
// 그대로 둠(2026-08-31 사용자 결정).
public final class PlaceRepresentativeImageResolver {

  private PlaceRepresentativeImageResolver() {
  }

  // 쇼핑(2026-08-31)에서 물량이 아주 적은 6개 세부분류가 공용으로 쓰는 이미지 1장 - ROTATION보다
  // 먼저 선언해야 아래 Map.entry에서 참조할 수 있다.
  private static final List<String> SHOPPING_ETC_IMAGE = List.of(
      "https://images.pexels.com/photos/13425897/pexels-photo-13425897.jpeg"
  );

  // 축제(2026-08-31)에서 실사진 없는 8건이 5개 세부분류에 흩어져 있어 공용으로 쓰는 이미지 1장.
  private static final List<String> FESTIVAL_ETC_IMAGE = List.of(
      "https://images.pexels.com/photos/24850993/pexels-photo-24850993.jpeg"
  );

  // Map.of()는 10쌍 제한이라 항목이 늘면서 ofEntries로 전환(2026-08-31).
  private static final Map<String, List<String>> ROTATION = Map.ofEntries(
      Map.entry("모텔", List.of(
          "https://images.pexels.com/photos/30171418/pexels-photo-30171418.jpeg",
          "https://images.pexels.com/photos/32016803/pexels-photo-32016803.jpeg",
          "https://images.pexels.com/photos/4913378/pexels-photo-4913378.jpeg"
      )),
      Map.entry("호텔", List.of(
          "https://images.pexels.com/photos/36428237/pexels-photo-36428237.jpeg",
          "https://images.pexels.com/photos/35551076/pexels-photo-35551076.jpeg",
          "https://images.pexels.com/photos/33330666/pexels-photo-33330666.jpeg"
      )),
      Map.entry("게스트하우스", List.of(
          "https://images.pexels.com/photos/17136827/pexels-photo-17136827.jpeg",
          "https://images.pexels.com/photos/17152859/pexels-photo-17152859.jpeg",
          "https://images.pexels.com/photos/5691318/pexels-photo-5691318.jpeg"
      )),
      // 박물관/미술관(2026-08-31) - 역사/과학박물관 465건, 상설미술관 398건 실사진 없음. 물량은
      // 숙박보다 적지만(각 4~500건) 사용자가 로테이션으로 진행하기로 결정.
      Map.entry("역사/과학박물관", List.of(
          "https://images.pexels.com/photos/16487537/pexels-photo-16487537.jpeg",
          "https://images.pexels.com/photos/38150086/pexels-photo-38150086.jpeg",
          "https://images.pexels.com/photos/11420936/pexels-photo-11420936.jpeg"
      )),
      Map.entry("상설미술관", List.of(
          "https://images.pexels.com/photos/9221307/pexels-photo-9221307.jpeg",
          "https://images.pexels.com/photos/13055169/pexels-photo-13055169.jpeg",
          "https://images.pexels.com/photos/297394/pexels-photo-297394.jpeg"
      )),
      // 맛집(2026-08-31) - 물량 기준으로 후보 수를 다르게 뒀다: 식당(한식)·카페(일반)은 3장,
      // 식당(일식/중식/분식/양식)은 2장, 나머지(물량 100건 미만)는 1장.
      Map.entry("식당(한식)", List.of(
          "https://images.pexels.com/photos/1213271/pexels-photo-1213271.jpeg",
          "https://images.pexels.com/photos/11186086/pexels-photo-11186086.jpeg",
          "https://images.pexels.com/photos/37469410/pexels-photo-37469410.jpeg"
      )),
      Map.entry("카페(일반)", List.of(
          "https://images.pexels.com/photos/34006322/pexels-photo-34006322.jpeg",
          "https://images.pexels.com/photos/32166402/pexels-photo-32166402.jpeg",
          "https://images.pexels.com/photos/34104248/pexels-photo-34104248.jpeg"
      )),
      Map.entry("식당(일식)", List.of(
          "https://images.pexels.com/photos/39212611/pexels-photo-39212611.jpeg",
          "https://images.pexels.com/photos/39212614/pexels-photo-39212614.jpeg"
      )),
      Map.entry("식당(중식)", List.of(
          "https://images.pexels.com/photos/32860331/pexels-photo-32860331.jpeg",
          "https://images.pexels.com/photos/32860319/pexels-photo-32860319.jpeg"
      )),
      Map.entry("식당(분식)", List.of(
          "https://images.pexels.com/photos/37323220/pexels-photo-37323220.jpeg",
          "https://images.pexels.com/photos/35366769/pexels-photo-35366769.jpeg"
      )),
      Map.entry("식당(양식)", List.of(
          "https://images.pexels.com/photos/765081/pexels-photo-765081.jpeg",
          "https://images.pexels.com/photos/20234577/pexels-photo-20234577.jpeg"
      )),
      Map.entry("식당(기타)", List.of(
          "https://images.pexels.com/photos/9738985/pexels-photo-9738985.jpeg"
      )),
      Map.entry("식당(뷔페)", List.of(
          "https://images.pexels.com/photos/34307855/pexels-photo-34307855.jpeg"
      )),
      Map.entry("식당(아시아음식)", List.of(
          "https://images.pexels.com/photos/28397638/pexels-photo-28397638.jpeg"
      )),
      Map.entry("캐주얼 맛집/브런치", List.of(
          "https://images.pexels.com/photos/5865690/pexels-photo-5865690.jpeg"
      )),
      Map.entry("카페(브런치)", List.of(
          "https://images.pexels.com/photos/33675674/pexels-photo-33675674.jpeg"
      )),
      Map.entry("술집/포차/이자카야", List.of(
          "https://images.pexels.com/photos/30641694/pexels-photo-30641694.jpeg"
      )),
      // 액티비티(2026-08-31) - 미보유 건수 기준(100건 이상 3장 / 10건 이상 2장 / 미만 1장). 액티비티에
      // 잘못 붙어있던 펜션/리조트/한옥 17건은 캠핑/글램핑(id 34)으로 재분류 완료 후의 수치 기준.
      Map.entry("볼링/당구/오락실", List.of(
          "https://images.pexels.com/photos/9821621/pexels-photo-9821621.jpeg",
          "https://images.pexels.com/photos/36609679/pexels-photo-36609679.jpeg",
          "https://images.pexels.com/photos/7429395/pexels-photo-7429395.jpeg"
      )),
      Map.entry("캠핑/글램핑", List.of(
          "https://images.pexels.com/photos/35843997/pexels-photo-35843997.jpeg",
          "https://images.pexels.com/photos/32602809/pexels-photo-32602809.jpeg",
          "https://images.pexels.com/photos/28224323/pexels-photo-28224323.jpeg"
      )),
      Map.entry("골프", List.of(
          "https://images.pexels.com/photos/6573252/pexels-photo-6573252.jpeg",
          "https://images.pexels.com/photos/5384079/pexels-photo-5384079.jpeg",
          "https://images.pexels.com/photos/164250/pexels-photo-164250.jpeg"
      )),
      Map.entry("보드게임카페", List.of(
          "https://images.pexels.com/photos/38348566/pexels-photo-38348566.jpeg",
          "https://images.pexels.com/photos/8907373/pexels-photo-8907373.jpeg",
          "https://images.pexels.com/photos/7047512/pexels-photo-7047512.jpeg"
      )),
      Map.entry("원데이클래스", List.of(
          "https://images.pexels.com/photos/33912318/pexels-photo-33912318.jpeg",
          "https://images.pexels.com/photos/30698565/pexels-photo-30698565.jpeg",
          "https://images.pexels.com/photos/33799212/pexels-photo-33799212.jpeg"
      )),
      Map.entry("클라이밍/스포츠체험", List.of(
          "https://images.pexels.com/photos/6674132/pexels-photo-6674132.jpeg",
          "https://images.pexels.com/photos/5916524/pexels-photo-5916524.jpeg"
      )),
      Map.entry("방탈출/미스터리룸", List.of(
          "https://images.pexels.com/photos/15801268/pexels-photo-15801268.jpeg",
          "https://images.pexels.com/photos/2674271/pexels-photo-2674271.jpeg"
      )),
      Map.entry("수상레포츠", List.of(
          "https://images.pexels.com/photos/14088842/pexels-photo-14088842.jpeg",
          "https://images.pexels.com/photos/33879055/pexels-photo-33879055.jpeg"
      )),
      Map.entry("낚시", List.of(
          "https://images.pexels.com/photos/30117794/pexels-photo-30117794.jpeg",
          "https://images.pexels.com/photos/37269841/pexels-photo-37269841.jpeg"
      )),
      Map.entry("스키/보드", List.of(
          "https://images.pexels.com/photos/31299014/pexels-photo-31299014.jpeg",
          "https://images.pexels.com/photos/10808864/pexels-photo-10808864.jpeg"
      )),
      Map.entry("승마", List.of(
          "https://images.pexels.com/photos/8624630/pexels-photo-8624630.jpeg"
      )),
      Map.entry("트레킹/산책길", List.of(
          "https://images.pexels.com/photos/34673805/pexels-photo-34673805.jpeg"
      )),
      Map.entry("VR/실감형체험", List.of(
          "https://images.pexels.com/photos/6498781/pexels-photo-6498781.jpeg"
      )),
      // 문화시설(2026-08-31) - 영화관은 별도 브랜드 매칭(CINEMA_BRAND_IMAGES)으로 처리하므로 여기엔
      // 없음. 나머지는 물량 기준(역사·이야기 전시는 사용자 요청으로 3장 상향, 나머지 2장).
      Map.entry("역사·이야기 전시", List.of(
          "https://images.pexels.com/photos/15070601/pexels-photo-15070601.jpeg",
          "https://images.pexels.com/photos/29057059/pexels-photo-29057059.jpeg",
          "https://images.pexels.com/photos/38965118/pexels-photo-38965118.jpeg"
      )),
      Map.entry("일반 전시", List.of(
          "https://images.pexels.com/photos/20030193/pexels-photo-20030193.jpeg",
          "https://images.pexels.com/photos/12705461/pexels-photo-12705461.jpeg"
      )),
      Map.entry("문화원", List.of(
          "https://images.pexels.com/photos/37431361/pexels-photo-37431361.jpeg",
          "https://images.pexels.com/photos/18623575/pexels-photo-18623575.jpeg"
      )),
      Map.entry("도서관", List.of(
          "https://images.pexels.com/photos/38323235/pexels-photo-38323235.jpeg",
          "https://images.pexels.com/photos/37700271/pexels-photo-37700271.jpeg"
      )),
      // 관광지(2026-08-31) - 자연/경관·골목/동네산책·역사/유적·전망/야경은 3장, 테마파크/온천·스파/
      // 랜드마크는 2장. 골목/동네산책·테마파크·랜드마크 전체와 역사/유적·온천 일부는 "우리는 해외를
      // 다루지 않는다"는 피드백으로 국내(위키미디어 실사진) 위주로 교체함.
      Map.entry("자연/경관", List.of(
          "https://images.pexels.com/photos/15103188/pexels-photo-15103188.png",
          "https://images.pexels.com/photos/34168792/pexels-photo-34168792.jpeg",
          "https://images.pexels.com/photos/19583624/pexels-photo-19583624.jpeg"
      )),
      Map.entry("골목/동네산책", List.of(
          "https://images.pexels.com/photos/33415083/pexels-photo-33415083.jpeg",
          "https://images.pexels.com/photos/33415075/pexels-photo-33415075.jpeg",
          "https://images.pexels.com/photos/37394218/pexels-photo-37394218.jpeg"
      )),
      Map.entry("역사/유적", List.of(
          "https://images.pexels.com/photos/38267989/pexels-photo-38267989.jpeg",
          "https://upload.wikimedia.org/wikipedia/commons/e/e2/Cheomseongdae-1.jpg",
          "https://upload.wikimedia.org/wikipedia/commons/8/8a/Hwaseomun_20081005.JPG"
      )),
      Map.entry("전망/야경", List.of(
          "https://images.pexels.com/photos/31295938/pexels-photo-31295938.jpeg",
          "https://images.pexels.com/photos/28376206/pexels-photo-28376206.jpeg",
          "https://images.pexels.com/photos/17625286/pexels-photo-17625286.jpeg"
      )),
      Map.entry("테마파크", List.of(
          "https://upload.wikimedia.org/wikipedia/commons/c/c2/Lotte_World_Theme_Park.jpg",
          "https://upload.wikimedia.org/wikipedia/commons/3/39/%EC%97%90%EB%B2%84%EB%9E%9C%EB%93%9C_%EB%86%80%EC%9D%B4%EA%B8%B0%EA%B5%AC_2025.jpg"
      )),
      Map.entry("온천/스파", List.of(
          "https://images.pexels.com/photos/37639956/pexels-photo-37639956.jpeg",
          "https://upload.wikimedia.org/wikipedia/commons/a/a0/%EC%9C%A0%EC%84%B1%EC%98%A8%EC%B2%9C_%EC%A1%B1%EC%9A%95%EC%B2%B4%ED%97%98%EC%9E%A5.png"
      )),
      Map.entry("랜드마크", List.of(
          "https://upload.wikimedia.org/wikipedia/commons/0/0f/N_Seoul_Tower_Panorama_001.jpg",
          "https://upload.wikimedia.org/wikipedia/commons/a/a6/%EA%B2%BD%EB%B3%B5%EA%B6%81_%EC%A0%84%EA%B2%BD.jpg"
      )),
      // 쇼핑(2026-08-31) - 실사진 없는 게 전통시장(54건) 빼고는 다 합쳐도 4건뿐이라, 전통시장만
      // 2장 두고 나머지 6개 세부분류는 공용 이미지 1장(SHOPPING_ETC_IMAGE)을 같이 쓴다.
      Map.entry("전통시장", List.of(
          "https://images.pexels.com/photos/37319817/pexels-photo-37319817.jpeg",
          "https://images.pexels.com/photos/37319820/pexels-photo-37319820.jpeg"
      )),
      Map.entry("브랜드 매장", SHOPPING_ETC_IMAGE),
      Map.entry("백화점", SHOPPING_ETC_IMAGE),
      Map.entry("아울렛", SHOPPING_ETC_IMAGE),
      Map.entry("편집숍/빈티지샵", SHOPPING_ETC_IMAGE),
      Map.entry("야시장", SHOPPING_ETC_IMAGE),
      Map.entry("복합쇼핑몰", SHOPPING_ETC_IMAGE),
      // 스포츠(2026-08-31) - 경기장 외관 대신 실제 경기 진행 중인 국내 사진(위키미디어)으로.
      // 농구/배구는 현재 실사진 미보유건이 0이지만(연결된 장소 자체가 없음) 향후 대비용으로 미리 준비.
      Map.entry("축구 직관", List.of(
          "https://upload.wikimedia.org/wikipedia/commons/5/50/Super_Match._03.August_2013.jpg"
      )),
      Map.entry("야구 직관", List.of(
          "https://upload.wikimedia.org/wikipedia/commons/f/fc/Doosan_v_LG_Audience.jpg"
      )),
      Map.entry("농구 직관", List.of(
          "https://upload.wikimedia.org/wikipedia/commons/b/b6/2%EC%9B%94_13%EC%9D%BC_%EC%84%9C%EC%9A%B8SK_VS_%EB%B6%80%EC%82%B0KT_%283%29.jpg"
      )),
      Map.entry("배구 직관", List.of(
          "https://upload.wikimedia.org/wikipedia/commons/2/21/Daejeon_Chungmu_Gymnasium_indoor_volleyball.jpg"
      )),
      // 축제(2026-08-31) - 실사진 없는 8건이 흩어진 5개 세부분류 전부 공용 이미지 1장.
      Map.entry("테마페스티벌", FESTIVAL_ETC_IMAGE),
      Map.entry("지역축제", FESTIVAL_ETC_IMAGE),
      Map.entry("문화예술축제", FESTIVAL_ETC_IMAGE),
      Map.entry("계절축제", FESTIVAL_ETC_IMAGE),
      Map.entry("전통제례/헤리티지나이트", FESTIVAL_ETC_IMAGE)
  );

  // 영화관 전용(2026-08-31) - 이미지 없는 212건 중 197건(93%)이 CGV/롯데시네마/메가박스 3대 체인이라
  // 발견해서, 세부분류 공용 스톡사진 대신 그 체인의 실제 매장 사진(위키미디어)을 이름으로 매칭해
  // 보여준다. 실존 체인의 진짜 사진(다만 그 특정 지점 사진은 아님)이라 스톡 이미지보다 사칭 소지가
  // 적다고 판단해, 이 경우엔 "실사진 준비중" 배지를 띄우지 않기로 함(2026-08-31 사용자 결정) - 반면
  // 브랜드가 안 걸리는 나머지 7%는 일반 스톡사진(CINEMA_FALLBACK)이라 배지를 그대로 띄운다.
  private static final Map<String, String> CINEMA_BRAND_IMAGES = Map.of(
      "CGV", "https://upload.wikimedia.org/wikipedia/commons/8/86/CGV_Cheongdam_Cine_City_2012.JPG",
      "롯데시네마", "https://upload.wikimedia.org/wikipedia/commons/e/ea/%EB%A1%AF%EB%8D%B0%EC%8B%9C%EB%84%A4%EB%A7%88_%EA%B0%95%EB%8F%99.jpg",
      "메가박스", "https://upload.wikimedia.org/wikipedia/commons/9/90/Megabox_Cineplex_Seoul.jpg"
  );
  private static final String CINEMA_FALLBACK =
      "https://images.pexels.com/photos/14746411/pexels-photo-14746411.jpeg";

  private static String matchedCinemaBrandImage(Place place) {
    if (place.getName() == null) {
      return null;
    }
    for (Map.Entry<String, String> e : CINEMA_BRAND_IMAGES.entrySet()) {
      if (place.getName().contains(e.getKey())) {
        return e.getValue();
      }
    }
    return null;
  }

  // place.imageUrl이 있으면 그걸 그대로 쓰고, 없을 때만 대표이미지(브랜드 매칭/로테이션/단일 픽)로 대체.
  public static String resolve(Place place, PlaceCategory category) {
    if (place.getImageUrl() != null) {
      return place.getImageUrl();
    }
    if (category == null) {
      return null;
    }
    if ("영화관".equals(category.getSubCategory())) {
      String brandImage = matchedCinemaBrandImage(place);
      return brandImage != null ? brandImage : CINEMA_FALLBACK;
    }
    List<String> rotation = ROTATION.get(category.getSubCategory());
    if (rotation != null && !rotation.isEmpty()) {
      int idx = (int) Math.floorMod(place.getId(), (long) rotation.size());
      return rotation.get(idx);
    }
    return category.getRepresentativeImageUrl();
  }

  // 실사진이 아니라 대표이미지로 대체됐는지 여부 - 프론트가 "실사진 준비중" 배지를 띄우는 기준.
  // 영화관 중 브랜드가 매칭된 경우(CGV/롯데시네마/메가박스 실사진)는 예외로 배지를 띄우지 않는다
  // (2026-08-31 사용자 결정) - 그 체인의 실존 매장 사진이라 스톡사진보다 사칭 소지가 적다고 판단.
  public static boolean isPlaceholder(Place place, PlaceCategory category) {
    if (place.getImageUrl() != null || category == null) {
      return false;
    }
    if ("영화관".equals(category.getSubCategory()) && matchedCinemaBrandImage(place) != null) {
      return false;
    }
    return resolve(place, category) != null;
  }
}

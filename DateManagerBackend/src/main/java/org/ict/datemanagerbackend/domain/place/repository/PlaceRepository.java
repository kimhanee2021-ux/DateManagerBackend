package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// JpaRepository<Place, Long>을 상속만 하면, save/findById/findAll/delete 같은 기본 CRUD 메서드를
// Spring Data JPA가 인터페이스만 보고 자동으로 구현체를 만들어줌 (직접 구현 코드 안 짜도 됨)
public interface PlaceRepository extends JpaRepository<Place, Long> {

  // 메서드 이름 자체가 쿼리가 됨 (Query Method) - "findBy" 뒤에 필드명을 이어붙이면
  // Spring Data JPA가 "WHERE external_source = ? AND external_id = ?" SQL을 자동 생성함.
  // KOPIS 동기화 시 "이 공연을 이미 저장한 적 있는지" 확인하는 용도로 사용.
  Optional<Place> findByExternalSourceAndExternalId(String externalSource, String externalId);

  // 큐레이션/코스빌더에서 카테고리(맛집, 숙박 등)별로 장소를 페이지 단위 조회할 때 사용.
  Page<Place> findByCategory(String category, Pageable pageable);

  // "같은 공연장(venueId)에서 하는 다른 공연들" 조회용(2026-08-27) - "장소(공연장) 자체가 아니라
  // 거기서 하는 공연이 중요하다"는 제품 방향에 따라 추가. 지금 보고 있는 공연 자신은 제외하고,
  // 공연 시작일이 빠른 순으로 보여준다.
  List<Place> findByVenueIdAndIdNotOrderByStartDateAsc(String venueId, Long id);

  // 같은 그룹핑을 venueId가 없는 소스(문화정보 전시 등, 2026-08-27)에도 적용 - KOPIS의 mt10id 같은
  // 깔끔한 시설 ID가 없어서, 장소명(venueName) 완전일치로 대신 그룹핑한다("국립현대미술관 서울관"의
  // 다른 전시 찾기 등). 표기 차이는 대상으로 삼지 않아 오탐 위험이 적다.
  List<Place> findByVenueNameAndIdNotOrderByStartDateAsc(String venueName, Long id);

  // "AI 코스 추천" 반경 필터용(2026-08-25 추가) - 사각형(위경도 범위)으로 1차로 넉넉히 추린 뒤,
  // 서비스 레이어에서 haversine으로 정확한 원형 반경만 다시 거른다. (latitude, longitude) 복합
  // 인덱스(idx_places_lat_lng)가 이미 있어서 이 조건으로도 빠르다.
  List<Place> findByLatitudeBetweenAndLongitudeBetween(
      Double minLat, Double maxLat, Double minLon, Double maxLon, Pageable pageable);

  // 큐레이션 탭(데이트/숙박) 조회용 - 카테고리/세부분류/지역/이름검색을 전부 선택적으로 조합한다.
  // 카테고리가 있을 때(있어야만 하는) 버전과 없을 때 버전 2개로 나눴다 - "category IN :list"에
  // 빈 리스트를 넘기면 Hibernate가 예외를 던지기 때문에, 카테고리 자체가 없는 경우까지 억지로
  // 한 쿼리에 합치지 않았다.
  //
  // r1/r2/r3인 이유: 충북/충남→대전, 경북/경남→대구로 지역을 통합하면서(2026-08-19), "대전" 하나를
  // 골라도 "대전"·"충청북도"·"충청남도" 세 키워드 중 하나라도 주소에 포함되면 매치돼야 한다. JPQL은
  // LIKE ANY(list) 같은 문법이 없어서, 최대 3개까지 필요한 이 프로젝트 특성상 파라미터 3개를 두고
  // 안 쓰는 자리는 null로 넘겨 건너뛰는 방식을 택했다(PlaceController.expandRegion 참고).
  // subCategory/keyword/district가 "필터 없음"일 때는 파라미터 자체를 null로 넘기고 "IS NULL OR" 로
  // 건너뛰지만, region(r1)은 항상 값이 있어야 하는 자리라 컨트롤러가 필터 없을 땐 빈 문자열("")을
  // 넘긴다 - address LIKE '%%'는 모든 행에 걸리므로 "지역 필터 없음"과 같은 효과를 낸다.
  // district는 구/시 세부 필터용(예: "중구")인데, "중구"/"동구"/"고성군" 같은 이름은 서울·부산·대구·
  // 대전·울산·강원·경남 등 여러 시/도가 똑같이 쓰고 있어서 district 하나만으로 address를 검색하면
  // 완전히 다른 지역이 섞여 들어온다(2026-08-19 발견). 그래서 district는 항상 region(r1/r2/r3)과
  // AND로 같이 걸어서, "이 시/도 범위 안에서 + 이 구/시 이름"을 동시에 만족해야만 매치되게 한다.
  // LEFT JOIN인 이유(중요): "p.placeCategory.subCategory"처럼 경로를 그냥 따라가면 JPQL이 파라미터
  // 값과 무관하게 항상 INNER JOIN으로 컴파일해버려서, place_category_id가 NULL인 장소(세부분류가
  // 아직 안 붙은 장소)가 subCategory로 필터링을 안 하는 경우에도 통째로 결과에서 빠져버린다
  // (2026-08-19 발견 - 부산 "문화시설" 803건이 전부 place_category_id NULL이라 목록이 텅 비었었음).
  // LEFT JOIN + pc.subCategory로 바꾸면 연결이 없는 장소도 일단 후보에 남고, subCategory를 실제로
  // 지정했을 때만 그 값과 비교해서 걸러진다.
  // energyTarget(에너지 게이지, 2026-08-20): 유저가 큐레이션 탭에서 직접 조정하는 "지금 원하는
  // 활력 정도"(0~100) - 아직 온보딩 성향값 저장 파이프라인이 없어서(팀원 담당, 진행 전) 저장된
  // 유저 성향으로 자동 매칭을 할 수 없는 동안의 임시 대안이다. null이면 CASE가 모든 행에 0을 줘서
  // 사실상 무시되고 기존 id DESC 정렬만 적용되며, 값이 있으면 "장소의 에너지 점수가 게이지 값과
  // 가까운 순"으로 정렬한다.
  // excludeOutdoor/indoorBoost(날씨 기반 실내외, 2026-08-20): 비가 오면(excludeOutdoor=true)
  // 실외 장소(isIndoor=0)는 결과에서 아예 뺀다(하드 필터) - 어차피 못 갈 곳이라서. 폭염/한파일 때는
  // (indoorBoost=true) 빼지는 않고 실내 장소를 정렬 우선순위로 앞에 둔다(소프트 부스트) - 극단
  // 날씨에도 야외 장소를 원할 수는 있으니 아예 안 보여주는 건 과함(2026-08-20 사용자 결정, 에너지
  // 게이지와 같은 "필터보다는 정렬" 원칙). 둘 다 false/null로 넘기면 아무 효과 없음(기존 동작 그대로).
  // scoreEnergy가 없는 장소(placeCategory 미연결)는 중립값(50) 기준으로 계산한다(COALESCE) - 정렬
  // 밖으로 밀려나지 않고 "보통"으로 취급됨.
  // isIndoor 기본값(2026-08-20 수정): 세부분류(placeCategory) 연결이 안 된 장소는 원래 무조건
  // "실내(1)"로 간주했는데, 실측해보니 관광지 14,007건 중 93%(13,065건)가 세부분류 미연결 상태라
  // 비가 와도 대부분의 관광지가 필터에서 안 걸러지는 문제가 있었다(2026-08-20, 실제 비 오는 날
  // 신촌 좌표로 테스트하다 발견 - 관광지는 필터링됐어야 할 942건만 정확히 걸러지고 나머지는 실내
  // 취급되어 그대로 남음). 관광지/축제는 세부분류 6개 전부 예외 없이 실외(0)로 매겨져 있어서, 세부
  // 분류가 안 붙은 장소도 대분류(p.category)가 관광지/축제면 실외로 기본값을 잡는 걸로 바꿨다 -
  // "고창읍성"·"갯벌" 같은 지역 특화 이름은 끝이 없어서 키워드 목록을 넓히는 대신 이 방식을 택함.
  // 다른 대분류(맛집/공연/액티비티/쇼핑 등)는 실내외가 섞여 있어서 이런 대분류 단위 기본값이 안전하지
  // 않아 그대로 실내(1) 기본값을 유지한다.
  // p.startDate ASC(2026-08-20): 공연/스포츠처럼 특정 날짜에만 열리는 장소는 데이터 생성순(id desc)이
  // 아니라 "곧 열리는 순"으로 보여야 실제로 갈 수 있는 것부터 보인다(사용자가 스포츠 카테고리에서
  // 실제로 발견함). startDate가 없는 장소(대부분의 카테고리)는 전부 null이라 서로 순위가 안 갈리고
  // 뒤의 정렬 기준으로 넘어가므로, 날짜가 있는 카테고리에만 자연스럽게 효과가 있다(오라클은 ASC에서
  // NULL을 가장 뒤로 보내는 게 기본 동작이라 별도 NULLS LAST 지정이 필요 없음).
  // sortByRank(2026-08-27, "공연 랭킹으로 필터링" 기능): KOPIS 박스오피스 순위(PerformanceRanking,
  // rank_no)가 있는 공연을 앞으로 보낸다. Place가 PerformanceRanking을 직접 참조하지 않아서(N:1의
  // N쪽 - 매일 순위표가 통째로 갈아끼워지는 구조라 반대 방향 연관관계를 안 만듦) JPA 2.1 "LEFT JOIN
  // ... ON" 문법으로 서로 다른 루트를 직접 조인한다. false일 때는 CASE가 전부 0을 줘서(다른 정렬
  // 기준들과 동일한 패턴) 기존 동작에 영향이 없고, true일 때만 순위 오름차순(순위 없는 곳은
  // COALESCE로 맨 뒤로) 정렬이 맨 앞 기준으로 붙는다.
  @Query("SELECT p FROM Place p LEFT JOIN p.placeCategory pc "
      + "LEFT JOIN PerformanceRanking pr ON pr.place = p "
      + "WHERE p.category IN :categories "
      + "AND (:subCategory IS NULL OR pc.subCategory = :subCategory) AND "
      + "(p.address LIKE CONCAT('%', :r1, '%') "
      + "OR (:r2 IS NOT NULL AND p.address LIKE CONCAT('%', :r2, '%')) "
      + "OR (:r3 IS NOT NULL AND p.address LIKE CONCAT('%', :r3, '%'))) "
      + "AND (:district IS NULL OR p.address LIKE CONCAT('%', :district, '%')) "
      + "AND (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%')) "
      + "AND (:excludeOutdoor = false OR COALESCE(pc.isIndoor, CASE WHEN p.category IN ('관광지', '축제') THEN 0 ELSE 1 END) = 1) "
      + "ORDER BY "
      + "CASE WHEN :sortByRank = false THEN 0 ELSE COALESCE(pr.rankNo, 999999) END ASC, "
      + "CASE WHEN :indoorBoost = false THEN 0 ELSE (1 - COALESCE(pc.isIndoor, CASE WHEN p.category IN ('관광지', '축제') THEN 0 ELSE 1 END)) END ASC, "
      + "p.startDate ASC, "
      + "CASE WHEN :energyTarget IS NULL THEN 0 "
      + "ELSE ABS(COALESCE(pc.scoreEnergy, 50) - :energyTarget) END ASC")
  Page<Place> searchByCategoryIn(
      List<String> categories, String subCategory, String r1, String r2, String r3, String district,
      String keyword, boolean excludeOutdoor, boolean indoorBoost, Integer energyTarget, boolean sortByRank,
      Pageable pageable);

  // 카테고리 칩을 아예 안 고른(=전체) 경우. 위 searchByCategoryIn과 지역/구시/키워드/날씨/에너지게이지
  // 조건은 동일하다.
  @Query("SELECT p FROM Place p LEFT JOIN p.placeCategory pc "
      + "LEFT JOIN PerformanceRanking pr ON pr.place = p WHERE "
      + "(p.address LIKE CONCAT('%', :r1, '%') "
      + "OR (:r2 IS NOT NULL AND p.address LIKE CONCAT('%', :r2, '%')) "
      + "OR (:r3 IS NOT NULL AND p.address LIKE CONCAT('%', :r3, '%'))) "
      + "AND (:district IS NULL OR p.address LIKE CONCAT('%', :district, '%')) "
      + "AND (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%')) "
      + "AND (:excludeOutdoor = false OR COALESCE(pc.isIndoor, CASE WHEN p.category IN ('관광지', '축제') THEN 0 ELSE 1 END) = 1) "
      + "ORDER BY "
      + "CASE WHEN :sortByRank = false THEN 0 ELSE COALESCE(pr.rankNo, 999999) END ASC, "
      + "CASE WHEN :indoorBoost = false THEN 0 ELSE (1 - COALESCE(pc.isIndoor, CASE WHEN p.category IN ('관광지', '축제') THEN 0 ELSE 1 END)) END ASC, "
      + "p.startDate ASC, "
      + "CASE WHEN :energyTarget IS NULL THEN 0 "
      + "ELSE ABS(COALESCE(pc.scoreEnergy, 50) - :energyTarget) END ASC")
  Page<Place> searchAll(
      String r1, String r2, String r3, String district, String keyword,
      boolean excludeOutdoor, boolean indoorBoost, Integer energyTarget, boolean sortByRank, Pageable pageable);

  // 숙박 탭 카테고리 칩에 "OO곳" 개수를 보여주기 위한 대분류 안 세부분류별 집계.
  // 세부분류가 아직 안 붙은(placeCategory가 null인) 장소는 이 결과에 안 잡힌다.
  // categories가 List인 이유: 큐레이션 칩 하나가 실제 Place.category 값 여러 개를 가리키는 경우가
  // 있다(searchByCategoryIn과 같은 이유) - "공연" 칩은 장르명(서양음악(클래식) 등) 10개로, "전시" 칩은
  // "문화시설"로, "박물관·미술관" 칩은 "박물관/미술관"(슬래시, 시더의 parent_category "박물관·미술관"과는
  // 다른 값)으로 실제 저장돼 있다. 예전엔 단일 String으로 받아 칩의 id를 그대로 넘겼는데, id와 실제
  // Place.category 값이 다른 칩(전시/공연/박물관·미술관)에서 세부칩 개수가 전부 0으로 나오는 버그가
  // 있었다(2026-08-28 사용자가 큐레이션 탭에서 실제로 발견 - 칩을 눌러 들어가면 결과는 나오는데
  // 칩에 찍힌 개수만 0인 상태).
  @Query("SELECT p.placeCategory.subCategory, COUNT(p) FROM Place p "
      + "WHERE p.category IN :categories AND p.placeCategory IS NOT NULL "
      + "GROUP BY p.placeCategory.subCategory")
  List<Object[]> countGroupedBySubCategory(List<String> categories);

  // 위와 같은 집계인데 지역까지 같이 좁힐 때 쓴다(2026-08-19, category-counts와 같은 이유).
  // r1/r2/r3/district는 위 searchByCategoryIn과 같은 이유(지역 통합 + 구시 중복이름 대응).
  @Query("SELECT p.placeCategory.subCategory, COUNT(p) FROM Place p "
      + "WHERE p.category IN :categories AND p.placeCategory IS NOT NULL "
      + "AND (p.address LIKE CONCAT('%', :r1, '%') "
      + "OR (:r2 IS NOT NULL AND p.address LIKE CONCAT('%', :r2, '%')) "
      + "OR (:r3 IS NOT NULL AND p.address LIKE CONCAT('%', :r3, '%'))) "
      + "AND (:district IS NULL OR p.address LIKE CONCAT('%', :district, '%')) "
      + "GROUP BY p.placeCategory.subCategory")
  List<Object[]> countGroupedBySubCategoryAndAddressContaining(
      List<String> categories, String r1, String r2, String r3, String district);

  // 홈탭 "내 주변"에서 스포츠처럼 희소한 카테고리용(2026-08-20) - findNearestPlaces(좌표 최근접 N개
  // 풀링 후 카테고리로 거르는 방식)은 전국에 몇십~몇백 곳뿐인 카테고리엔 안 맞는다. 흔한 카테고리
  // (맛집 등) 수천~수만 건이 이미 최근접 풀을 다 채워버려서, 정작 몇 km만 더 가면 있는 경기장이
  // 풀에 아예 안 들어가 결과가 0건이 되는 문제가 있었다(PlaceController.listNearbyPlaces 참고). 그래서
  // 좌표 거리 대신 "같은 시/도"(카카오 좌표->행정구역 API로 역지오코딩) 기준으로 찾고, 어차피 날짜가
  // 있는 카테고리라 startDate 오름차순(곧 열리는 순)으로 정렬한다.
  // subCategory(2026-08-20 확장): "공연" 대분류는 전체로 보면 흔하지만(연극/뮤지컬/클래식 등 합쳐
  // 수천 건), 그중 "대형 콘서트"·"뮤직페스티벌"만 따로 보면 전국 471건/36건뿐이라 똑같이 희소하다
  // (사용자가 실제로 이 문제를 지적함). null이면(스포츠처럼 세부분류 구분이 필요 없는 카테고리)
  // 대분류만으로 걸러 기존과 동일하게 동작한다 - LEFT JOIN인 이유는 searchByCategoryIn과 같다
  // (세부분류 미연결 장소가 subCategory 조건 없을 때 결과에서 안 빠지게).
  // startDate >= :today(2026-08-28 추가): 이 필터가 없으면 "곧 열리는 순"이 아니라 "날짜가 이른 순"이
  // 돼서, 어제 끝난 경기가 정리 배치(하루 1회) 전까지 남아있는 동안 그게 오늘/내일 경기보다 먼저
  // 나오는 문제가 있었다(스포츠 3연전에서 실측 - 사용자 지적). startDate가 없는 장소(날짜 개념이 없는
  // 카테고리)까지 이 필터로 걸러지면 안 되니 null 허용.
  @Query("SELECT p FROM Place p LEFT JOIN p.placeCategory pc WHERE p.category = :category "
      + "AND (:subCategory IS NULL OR pc.subCategory = :subCategory) "
      + "AND p.address LIKE CONCAT('%', :addressKeyword, '%') "
      + "AND (p.startDate IS NULL OR p.startDate >= :today) "
      + "ORDER BY p.startDate ASC")
  List<Place> findSparseByCategoryAndAddress(String category, String subCategory, String addressKeyword,
                                              java.time.LocalDate today);

  // 이름에 특정 키워드가 포함된 장소를 찾는다 - 프랜차이즈/체인점 블랙리스트 정리용
  // (TourApiSyncService.cleanupBlacklistedPlaces() 참고).
  List<Place> findByNameContaining(String keyword);

  // 아직 세부분류(place_category)가 안 붙은 장소만 골라서 자동 연결 배치(PlaceCategoryLinker)가 처리한다.
  List<Place> findByPlaceCategoryIsNull();

  // TourAPI 상세정보(detailIntro2/detailImage2) 동기화 대상 - 개발계정 일일 호출 제한 때문에 매번
  // limit(Pageable)만큼만 잘라서 가져온다(findByPlaceCategoryIsNull처럼 전체를 메모리로 다 불러온 뒤
  // 자르는 방식은 4만~8만 건 규모에서 비효율적이라 여기서는 처음부터 페이징 쿼리로 만듦, 2026-08-26).
  @Query("SELECT p FROM Place p WHERE p.externalSource = 'TOURAPI' AND p.detailSynced IS NULL ORDER BY p.id")
  List<Place> findTourApiPlacesPendingDetailSync(Pageable pageable);

  // 위 커서를 카테고리별 라운드로빈으로 도는 용도(2026-08-27) - id 순서로만 돌면 id가 낮은 카테고리
  // (맛집)만 몇 주째 다 채우고 나머지(관광지/숙박 등)는 하나도 못 건드리는 문제가 있었다. 배치마다
  // "아직 안 끝난 카테고리 목록"과 "카테고리별로 다음 N건"을 따로 조회해 서비스 레이어에서 섞는다.
  @Query("SELECT DISTINCT p.category FROM Place p WHERE p.externalSource = 'TOURAPI' AND p.detailSynced IS NULL")
  List<String> findCategoriesPendingTourApiDetailSync();

  @Query("SELECT p FROM Place p WHERE p.externalSource = 'TOURAPI' AND p.detailSynced IS NULL AND p.category = :category ORDER BY p.id")
  List<Place> findTourApiPlacesPendingDetailSyncByCategory(@Param("category") String category, Pageable pageable);

  // 관리자 페이지에서 카테고리별 수집량을 확인할 때 사용. TourAPI/KOPIS/박물관 등 소스마다
  // 카테고리 값이 다양해서(관광지/문화시설/공연/액티비티/쇼핑/맛집/숙박/박물관·미술관 등)
  // 하드코딩하지 않고 실제 저장된 값을 그대로 집계한다. 결과의 각 Object[]는 [category, count].
  @Query("SELECT p.category, COUNT(p) FROM Place p GROUP BY p.category ORDER BY COUNT(p) DESC")
  List<Object[]> countGroupedByCategory();

  // 큐레이션 탭 카테고리 칩의 "N곳" 표시가 지역 필터랑 무관하게 항상 전국 건수만 보여주고 있던 문제
  // 수정용(2026-08-19, 사용자가 실제 화면에서 발견) - region이 있으면 그 지역 안에서만 집계한다.
  // r1/r2/r3/district는 위 searchByCategoryIn과 같은 이유(지역 통합 + 구시 중복이름 대응).
  @Query("SELECT p.category, COUNT(p) FROM Place p WHERE "
      + "(p.address LIKE CONCAT('%', :r1, '%') "
      + "OR (:r2 IS NOT NULL AND p.address LIKE CONCAT('%', :r2, '%')) "
      + "OR (:r3 IS NOT NULL AND p.address LIKE CONCAT('%', :r3, '%'))) "
      + "AND (:district IS NULL OR p.address LIKE CONCAT('%', :district, '%')) "
      + "GROUP BY p.category ORDER BY COUNT(p) DESC")
  List<Object[]> countGroupedByCategoryAndAddressContaining(String r1, String r2, String r3, String district);

  // 전체 장소 백업(CSV export, AdminController)용 - place_category를 LEFT JOIN 페치해서 한 번의
  // 쿼리로 다 가져온다. 엔티티로 84,000여건을 로드하면서 placeCategory를 건마다 지연 로딩하면
  // N+1이 나서, 필요한 컬럼만 프로젝션으로 뽑는다(2026-08-14).
  @Query("SELECT p.id, p.name, p.category, pc.subCategory, pc.emoji, p.address, p.latitude, p.longitude, "
      + "p.externalSource, pc.scoreEnergy, pc.scoreImmersion, pc.scoreVibe, pc.scoreAesthetic, pc.scoreDepth "
      + "FROM Place p LEFT JOIN p.placeCategory pc ORDER BY p.id")
  List<Object[]> findAllForExport();

  // 같은 실제 장소가 TourAPI/KOPIS/네이버/카카오 등 서로 다른 소스에서 각각 들어와 중복 저장되는 걸
  // 막기 위해, 새 장소를 저장하기 전에 좌표가 가까운(대략 위경도 사각형 범위) 기존 장소들을 먼저 찾아서
  // PlaceDedupService가 이름까지 비교한다. 위경도 컬럼엔 인덱스가 없어 범위가 아주 넓으면 느릴 수 있지만,
  // 반경을 좁게(약 50m) 잡아서 쓰므로 실사용에는 문제없다.
  @Query("SELECT p FROM Place p WHERE p.latitude BETWEEN :minLat AND :maxLat AND p.longitude BETWEEN :minLng AND :maxLng")
  List<Place> findNearby(double minLat, double maxLat, double minLng, double maxLng);

  // 홈탭 "지역 기반 추천"용 - 접속 좌표에서 가까운 순으로 정렬해서 상위 N개를 돌려준다.
  // 위경도에 인덱스가 없어(위 findNearby 주석 참고) 넓은 사각형(±1도, 대략 100km대)으로 먼저
  // 후보를 줄인 다음, 그 안에서만 실제 거리(하버사인 공식)를 계산해 정렬한다(2026-08-13 추가).
  // 서브쿼리에서 ORDER BY까지 끝낸 뒤 바깥에서 ROWNUM으로 자르는 건 오라클의 표준 페이징 패턴.
  // 오라클엔 RADIANS() 함수가 없어서 degrees * (PI/180) = 0.0174532925199433 을 직접 곱해서 변환한다.
  @Query(value = "SELECT * FROM ("
      + "  SELECT p.*, (6371 * ACOS(LEAST(1, GREATEST(-1, "
      + "    COS(:lat * 0.0174532925199433) * COS(p.latitude * 0.0174532925199433) "
      + "      * COS(p.longitude * 0.0174532925199433 - :lon * 0.0174532925199433) "
      + "    + SIN(:lat * 0.0174532925199433) * SIN(p.latitude * 0.0174532925199433)"
      + "  )))) AS dist_km "
      + "  FROM places p "
      + "  WHERE p.latitude IS NOT NULL AND p.longitude IS NOT NULL "
      + "    AND p.latitude BETWEEN :lat - 1.0 AND :lat + 1.0 "
      + "    AND p.longitude BETWEEN :lon - 1.0 AND :lon + 1.0 "
      + "  ORDER BY dist_km ASC"
      + ") WHERE ROWNUM <= :limit",
      nativeQuery = true)
  List<Place> findNearestPlaces(double lat, double lon, int limit);

  // PlaceCoordinateVerificationService가 TourAPI 출처 장소를 배치로 재검증할 때 쓴다(2026-08-20,
  // "홍원" 좌표 오차 발견 후 전체 재검증). coordinateVerified가 NULL인 기존 행까지 다 걸리게
  // "IS NULL OR = false"로 명시한다 - Oracle은 <> 비교에서 NULL을 매치 안 시켜주는 3치 논리라
  // derived "...Not(true)" 메서드로는 NULL 행을 못 잡는다.
  @Query("SELECT p FROM Place p WHERE p.externalSource = :externalSource "
      + "AND (p.coordinateVerified IS NULL OR p.coordinateVerified = false)")
  Page<Place> findUnverifiedTourApiPlaces(String externalSource, Pageable pageable);

  // 네이버 지역 검색처럼 검색어 기반이라 매 실행마다 결과가 달라질 수 있는 소스는 upsert 대신
  // "이번 실행 전 기존 데이터를 지우고 새로 채우는" 전체 교체 방식을 쓴다 - 폐업한 곳도 자동 정리됨.
  // derived delete 메서드는 대상 엔티티를 조회한 뒤 개별 remove()를 호출하는 방식이라, 호출하는 쪽에
  // 트랜잭션이 없으면 "No EntityManager with actual transaction available" 에러가 나서 직접 @Transactional을 붙인다.
  @Transactional
  void deleteByExternalSource(String externalSource);

}

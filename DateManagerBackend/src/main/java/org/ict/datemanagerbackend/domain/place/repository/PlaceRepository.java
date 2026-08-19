package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
  @Query("SELECT p FROM Place p LEFT JOIN p.placeCategory pc WHERE p.category IN :categories "
      + "AND (:subCategory IS NULL OR pc.subCategory = :subCategory) AND "
      + "(p.address LIKE CONCAT('%', :r1, '%') "
      + "OR (:r2 IS NOT NULL AND p.address LIKE CONCAT('%', :r2, '%')) "
      + "OR (:r3 IS NOT NULL AND p.address LIKE CONCAT('%', :r3, '%'))) "
      + "AND (:district IS NULL OR p.address LIKE CONCAT('%', :district, '%')) "
      + "AND (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%'))")
  Page<Place> searchByCategoryIn(
      List<String> categories, String subCategory, String r1, String r2, String r3, String district,
      String keyword, Pageable pageable);

  // 카테고리 칩을 아예 안 고른(=전체) 경우. 위 searchByCategoryIn과 지역/구시/키워드 조건은 동일하다.
  @Query("SELECT p FROM Place p WHERE "
      + "(p.address LIKE CONCAT('%', :r1, '%') "
      + "OR (:r2 IS NOT NULL AND p.address LIKE CONCAT('%', :r2, '%')) "
      + "OR (:r3 IS NOT NULL AND p.address LIKE CONCAT('%', :r3, '%'))) "
      + "AND (:district IS NULL OR p.address LIKE CONCAT('%', :district, '%')) "
      + "AND (:keyword IS NULL OR p.name LIKE CONCAT('%', :keyword, '%'))")
  Page<Place> searchAll(String r1, String r2, String r3, String district, String keyword, Pageable pageable);

  // 숙박 탭 카테고리 칩에 "OO곳" 개수를 보여주기 위한 대분류 안 세부분류별 집계.
  // 세부분류가 아직 안 붙은(placeCategory가 null인) 장소는 이 결과에 안 잡힌다.
  @Query("SELECT p.placeCategory.subCategory, COUNT(p) FROM Place p "
      + "WHERE p.category = :category AND p.placeCategory IS NOT NULL "
      + "GROUP BY p.placeCategory.subCategory")
  List<Object[]> countGroupedBySubCategory(String category);

  // 위와 같은 집계인데 지역까지 같이 좁힐 때 쓴다(2026-08-19, category-counts와 같은 이유).
  // r1/r2/r3/district는 위 searchByCategoryIn과 같은 이유(지역 통합 + 구시 중복이름 대응).
  @Query("SELECT p.placeCategory.subCategory, COUNT(p) FROM Place p "
      + "WHERE p.category = :category AND p.placeCategory IS NOT NULL "
      + "AND (p.address LIKE CONCAT('%', :r1, '%') "
      + "OR (:r2 IS NOT NULL AND p.address LIKE CONCAT('%', :r2, '%')) "
      + "OR (:r3 IS NOT NULL AND p.address LIKE CONCAT('%', :r3, '%'))) "
      + "AND (:district IS NULL OR p.address LIKE CONCAT('%', :district, '%')) "
      + "GROUP BY p.placeCategory.subCategory")
  List<Object[]> countGroupedBySubCategoryAndAddressContaining(
      String category, String r1, String r2, String r3, String district);

  // 이름에 특정 키워드가 포함된 장소를 찾는다 - 프랜차이즈/체인점 블랙리스트 정리용
  // (TourApiSyncService.cleanupBlacklistedPlaces() 참고).
  List<Place> findByNameContaining(String keyword);

  // 아직 세부분류(place_category)가 안 붙은 장소만 골라서 자동 연결 배치(PlaceCategoryLinker)가 처리한다.
  List<Place> findByPlaceCategoryIsNull();

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

  // 네이버 지역 검색처럼 검색어 기반이라 매 실행마다 결과가 달라질 수 있는 소스는 upsert 대신
  // "이번 실행 전 기존 데이터를 지우고 새로 채우는" 전체 교체 방식을 쓴다 - 폐업한 곳도 자동 정리됨.
  // derived delete 메서드는 대상 엔티티를 조회한 뒤 개별 remove()를 호출하는 방식이라, 호출하는 쪽에
  // 트랜잭션이 없으면 "No EntityManager with actual transaction available" 에러가 나서 직접 @Transactional을 붙인다.
  @Transactional
  void deleteByExternalSource(String externalSource);

}

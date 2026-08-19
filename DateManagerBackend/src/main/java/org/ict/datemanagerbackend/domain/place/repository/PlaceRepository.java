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

  // "공연" 칩처럼 실제로는 여러 category 값(KOPIS 장르명: 연극/뮤지컬/서양음악(클래식) 등)을 한 번에
  // 묶어서 보여줘야 하는 경우용 - PlaceSyncService가 공연은 "공연"이 아니라 genrenm(장르명) 그대로를
  // category에 저장하기 때문에 필요하다 (2026-08-14, 큐레이션 탭 실데이터 연동 중 발견).
  Page<Place> findByCategoryIn(List<String> categories, Pageable pageable);

  // 숙박 탭처럼 대분류 안에서 세부분류(placeCategory.subCategory)로 한 번 더 좁혀야 하는 경우용
  // (2026-08-14, 숙박 카테고리 칩 복원하면서 추가).
  Page<Place> findByCategoryAndPlaceCategory_SubCategory(String category, String subCategory, Pageable pageable);

  // 큐레이션 탭 지역 필터용(2026-08-19). 별도 시/도 컬럼이 없어서 address에 지역명이 포함되는지로
  // 판단한다(예: "서울" -> "서울특별시 강남구 ..."에 포함됨). 카테고리 필터와 조합되는 3가지 경우를
  // 각각 메서드로 둔다 - Spring Data가 메서드 이름만 보고 쿼리를 자동 생성해준다.
  Page<Place> findByCategoryInAndAddressContaining(List<String> categories, String region, Pageable pageable);

  Page<Place> findByCategoryInAndPlaceCategory_SubCategoryAndAddressContaining(
      List<String> categories, String subCategory, String region, Pageable pageable);

  Page<Place> findByAddressContaining(String region, Pageable pageable);

  // 숙박 탭 카테고리 칩에 "OO곳" 개수를 보여주기 위한 대분류 안 세부분류별 집계.
  // 세부분류가 아직 안 붙은(placeCategory가 null인) 장소는 이 결과에 안 잡힌다.
  @Query("SELECT p.placeCategory.subCategory, COUNT(p) FROM Place p "
      + "WHERE p.category = :category AND p.placeCategory IS NOT NULL "
      + "GROUP BY p.placeCategory.subCategory")
  List<Object[]> countGroupedBySubCategory(String category);

  // 위와 같은 집계인데 지역까지 같이 좁힐 때 쓴다(2026-08-19, category-counts와 같은 이유).
  @Query("SELECT p.placeCategory.subCategory, COUNT(p) FROM Place p "
      + "WHERE p.category = :category AND p.placeCategory IS NOT NULL "
      + "AND p.address LIKE CONCAT('%', :region, '%') "
      + "GROUP BY p.placeCategory.subCategory")
  List<Object[]> countGroupedBySubCategoryAndAddressContaining(String category, String region);

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
  @Query("SELECT p.category, COUNT(p) FROM Place p WHERE p.address LIKE CONCAT('%', :region, '%') "
      + "GROUP BY p.category ORDER BY COUNT(p) DESC")
  List<Object[]> countGroupedByCategoryAndAddressContaining(String region);

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

package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.PlaceAmenity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceAmenityRepository extends JpaRepository<PlaceAmenity, Long> {

  // 큐레이션 화면에서 여러 장소를 한 번에 내려줄 때 N+1 쿼리를 피하려고 배치 조회.
  List<PlaceAmenity> findByPlace_IdIn(List<Long> placeIds);

  // TourAPI 상세정보 동기화(2026-08-26)에서 같은 태그를 중복 저장하지 않으려고 미리 확인한다.
  boolean existsByPlace_IdAndAmenityTag(Long placeId, String amenityTag);

}

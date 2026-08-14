package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.PlaceReality;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceRealityRepository extends JpaRepository<PlaceReality, Long> {

  Optional<PlaceReality> findByPlace_Id(Long placeId);

  // 큐레이션 화면에서 여러 장소를 한 번에 내려줄 때 N+1 쿼리를 피하려고 배치 조회.
  List<PlaceReality> findByPlace_IdIn(List<Long> placeIds);

}

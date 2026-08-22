package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.PerformanceRanking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceRankingRepository extends JpaRepository<PerformanceRanking, Long> {

  // 순위 낮은(1위부터) 순으로 전체 랭킹을 내려줄 때 사용
  List<PerformanceRanking> findAllByOrderByRankNoAsc();

  // 큐레이션 화면에서 여러 장소를 한 번에 내려줄 때 N+1 쿼리를 피하려고 배치 조회 (PlaceReality와 동일한 이유)
  List<PerformanceRanking> findByPlace_IdIn(List<Long> placeIds);

}

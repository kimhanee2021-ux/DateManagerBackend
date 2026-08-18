package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.PerformanceRanking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceRankingRepository extends JpaRepository<PerformanceRanking, Long> {

  // 순위 낮은(1위부터) 순으로 전체 랭킹을 내려줄 때 사용
  List<PerformanceRanking> findAllByOrderByRankNoAsc();

}

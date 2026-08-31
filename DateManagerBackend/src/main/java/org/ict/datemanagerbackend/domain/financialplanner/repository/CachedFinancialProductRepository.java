package org.ict.datemanagerbackend.domain.financialplanner.repository;

import org.ict.datemanagerbackend.domain.financialplanner.entity.CachedFinancialProduct;
import org.ict.datemanagerbackend.domain.financialplanner.entity.ProductType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CachedFinancialProductRepository extends JpaRepository<CachedFinancialProduct, Long> {

  Optional<CachedFinancialProduct> findByFinCoNoAndFinPrdtCdAndSaveTrmAndProductType(
      String finCoNo, String finPrdtCd, Integer saveTrm, ProductType productType);

  // 대시보드 추천 목록 - 목표 기간(개월)과 가장 가까운 예치기간 상품을 최고우대금리 내림차순으로.
  List<CachedFinancialProduct> findBySaveTrmOrderByIntrRate2Desc(Integer saveTrm, Pageable pageable);

  // finlife 상품은 예치기간이 1/3/6/12/24/36개월처럼 정해진 값만 존재해서(2026-08-31 실측), 목표
  // 기간이 그 값과 정확히 안 맞으면(예: 10개월) 추천 상품이 0건이 되는 문제가 있었다 - 실제로 존재하는
  // 예치기간 값 목록을 뽑아서, 서비스 레이어에서 목표 기간과 가장 가까운 값을 골라 쓴다.
  @Query("SELECT DISTINCT p.saveTrm FROM CachedFinancialProduct p ORDER BY p.saveTrm")
  List<Integer> findDistinctSaveTrms();
}

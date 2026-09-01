package org.ict.datemanagerbackend.domain.financialplanner.service;

public interface FinlifeSyncService {
  // <<금융감독원 finlife API에서 예금+적금 상품을 받아 캐시 테이블에 upsert>>
  void syncProducts();
}

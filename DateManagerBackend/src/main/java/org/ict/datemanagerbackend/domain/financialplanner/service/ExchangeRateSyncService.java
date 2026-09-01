package org.ict.datemanagerbackend.domain.financialplanner.service;

public interface ExchangeRateSyncService {
  // <<한국수출입은행 환율 API에서 전체 통화 환율을 받아 캐시 테이블에 upsert>>
  void syncRates();
}

package org.ict.datemanagerbackend.domain.place.service;

import java.util.Map;

// 한국관광공사 TourAPI 동기화 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과 통일하기
// 위해 분리함(2026-08-19). 실제 동기화/필터링 로직은 TourApiSyncServiceImpl 참고.
public interface TourApiSyncService {
  // <<TourAPI 지역기반 목록조회로 장소 동기화 - 매일 새벽 4시 자동 실행>>
  void syncPlaces();
  // <<블랙리스트/프랜차이즈/병의원/시장 중복 등 이미 저장된 노이즈 정리(관리자 수동 트리거)>>
  Map<String, Integer> cleanupBlacklistedPlaces();
}

package org.ict.datemanagerbackend.domain.place.service;

// 네이버 지역 검색 API 동기화 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과 통일하기
// 위해 분리함(2026-08-19). 실제 동기화 로직은 NaverPlaceSyncServiceImpl 참고.
public interface NaverPlaceSyncService {
  // <<네이버 지역 검색으로 장소 동기화(검색어 기반이라 매번 전체 교체 방식)>>
  void syncPlaces();
}

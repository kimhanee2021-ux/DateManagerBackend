package org.ict.datemanagerbackend.domain.place.service;

// 카카오 로컬 API(키워드 검색) 동기화 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과
// 통일하기 위해 분리함(2026-08-19). 실제 동기화 로직은 KakaoPlaceSyncServiceImpl 참고.
public interface KakaoPlaceSyncService {
  // <<카카오 로컬 API로 지역×카테고리 조합 검색해서 장소 동기화(검색어 기반, 전체 교체 방식)>>
  void syncPlaces();
}

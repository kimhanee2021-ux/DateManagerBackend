package org.ict.datemanagerbackend.domain.place.service;

// 한국문화정보원 문화정보조회서비스(전시/축제/체험) 동기화 서비스의 인터페이스. admin 도메인의
// interface+impl 패턴과 통일하기 위해 분리함(2026-08-19). 실제 동기화 로직은
// CultureEventSyncServiceImpl 참고.
public interface CultureEventSyncService {
  // <<문화정보(전시/축제/체험) 동기화>>
  void syncEvents();
}

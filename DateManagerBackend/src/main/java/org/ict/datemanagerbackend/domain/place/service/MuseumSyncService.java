package org.ict.datemanagerbackend.domain.place.service;

// 전국박물관미술관표준데이터 동기화 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과
// 통일하기 위해 분리함(2026-08-19). 실제 동기화 로직은 MuseumSyncServiceImpl 참고.
public interface MuseumSyncService {
  // <<박물관/미술관 표준데이터 동기화>>
  void syncMuseums();
}

package org.ict.datemanagerbackend.domain.place.service;

// KOPIS(공연예술통합전산망) 공연/축제/박스오피스 동기화 서비스의 인터페이스. admin 도메인의
// interface+impl 패턴과 통일하기 위해 분리함(2026-08-19). 실제 동기화 로직/설계 배경은
// PlaceSyncServiceImpl 참고.
public interface PlaceSyncService {
  // <<KOPIS 공연 목록(향후 60일) 동기화 - 매일 새벽 3시 자동 실행>>
  void syncPerformances();
  // <<KOPIS 축제 목록 동기화 - 매일 새벽 3시30분 자동 실행>>
  void syncFestivals();
  // <<KOPIS 박스오피스(예매율 순위) 장르별 동기화 - 매일 새벽 3시40분 자동 실행>>
  void syncBoxOffice();
}

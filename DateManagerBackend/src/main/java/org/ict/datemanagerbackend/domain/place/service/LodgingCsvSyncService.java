package org.ict.datemanagerbackend.domain.place.service;

import java.util.Map;

// 문화_숙박업.csv(localdata.go.kr) 임포트 서비스의 인터페이스. admin 도메인의 interface+impl
// 패턴과 통일하기 위해 분리함(2026-08-19). 실제 임포트 로직은 LodgingCsvSyncServiceImpl 참고.
public interface LodgingCsvSyncService {
  // <<CSV 파일에서 숙박 데이터를 읽어 동기화>>
  Map<String, Integer> syncFromCsv();
}

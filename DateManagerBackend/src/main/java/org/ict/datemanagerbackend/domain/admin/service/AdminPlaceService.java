package org.ict.datemanagerbackend.domain.admin.service;

import java.util.Map;

public interface AdminPlaceService {
  // <<장소 동기화 수동 트리거 - source가 유효하지 않으면 IllegalArgumentException>>
  void syncSource(String source);
  // <<블랙리스트 필터 도입 이전에 저장된 프랜차이즈/체인점 정리>>
  Map<String, Integer> cleanupBlacklistedPlaces();
  // <<대분류별 장소 개수 + 전체 개수>>
  Map<String, Long> getSyncStatus();
  // <<전체 장소 백업(CSV 본문)>>
  String exportPlacesCsv();
  // <<장소 세부분류(성향점수 공식) 전체 백업(CSV 본문)>>
  String exportPlaceCategoriesCsv();
}

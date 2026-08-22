package org.ict.datemanagerbackend.domain.place.service;

public interface NaverCategoryMatchService {

  record MatchResult(
      int attempted,          // 이번 호출에서 시도한 장소 수
      int matched,             // 실제로 place_category가 연결된 수
      int apiNoResult,         // 네이버 검색 결과 자체가 없었던 수
      int noConfidentCandidate,// 결과는 있지만 좌표/이름이 안 맞아 우리 장소로 확신 못한 수
      int noKeywordMatch       // 후보는 확정했지만 네이버 category를 우리 세부분류로 못 옮긴 수
  ) {
  }

  MatchResult matchUnclassifiedPlaces(int limit);
}

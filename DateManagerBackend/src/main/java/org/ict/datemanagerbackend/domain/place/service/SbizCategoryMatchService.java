package org.ict.datemanagerbackend.domain.place.service;

public interface SbizCategoryMatchService {

  record MatchResult(
      int attempted,          // 이번 호출에서 시도한 장소 수
      int matched,             // 실제로 place_category가 연결된 수
      int apiNoResult,         // 반경 내에 후보 자체가 없었던 수
      int noConfidentCandidate,// 후보는 있지만 이름이 안 맞아 우리 장소로 확신 못한 수
      int noKeywordMatch,      // 후보는 확정했지만 업종명을 우리 세부분류로 못 옮긴 수
      int apiError,            // API 호출 자체가 실패한 수
      boolean stoppedEarly,    // 연속 실패가 많아 중간에 중단했는지 여부
      int closureSuspected     // 소상공인+카카오 둘 다 이름 일치 후보를 못 찾아 폐업 추정으로 표시한 수
  ) {
  }

  MatchResult matchUnclassifiedPlaces(int limit);
}

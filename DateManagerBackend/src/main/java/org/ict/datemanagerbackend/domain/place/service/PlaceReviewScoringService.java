package org.ict.datemanagerbackend.domain.place.service;

import org.ict.datemanagerbackend.domain.place.dto.PlaceReviewResultDto;

import java.util.List;

// 리뷰 기반 장소별 점수 보정(2026-08-25 착수) - OpenAI web_search로 실제 리뷰를 찾아 6축 성향
// 점수/평점/사진을 장소 하나하나에 채워 넣는다. 비용이 드는 외부 호출이라 처음엔 지정한 장소
// id 목록으로만 소규모 파일럿을 돌려보고, 결과 품질을 확인한 뒤 범위를 넓히는 흐름을 전제로 한다.
public interface PlaceReviewScoringService {
  List<PlaceReviewResultDto> runReviewPilot(List<Long> placeIds);
}

package org.ict.datemanagerbackend.domain.aichat.dto;

import java.util.List;

// 장소 상세 화면 "AI 코치의 한마디" - 유저(커플) 성향과 장소 성향을 대조해 왜 이 장소가 맞는지
// 짧게 설명하고, 그 근거가 된 상위 1~2개 축을 topAxes로 같이 내려준다(2026-08-31).
public record PlaceCoachExplanationDto(String message, List<String> topAxes) {
}

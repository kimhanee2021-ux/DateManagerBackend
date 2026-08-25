package org.ict.datemanagerbackend.domain.aichat.dto;

// 홈탭 "최근 관심사" 인사이트 칩용(2026-08-25 추가) - 챗봇 메시지 분석 시점에 저장만 되고
// 아무도 안 읽던 AiChatMessageIntent를 처음으로 집계해서 보여준다. count가 0이면(대화 이력이
// 없거나 의도가 한 번도 안 잡혔으면) 프론트가 이 칩 자체를 숨긴다.
public record IntentSummaryDto(String intentTag, String label, long count) {
}

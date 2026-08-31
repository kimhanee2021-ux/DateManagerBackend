package org.ict.datemanagerbackend.domain.aichat.dto;

// 홈탭 "최근 관심사" 인사이트 칩용(2026-08-25 추가) - 챗봇 메시지 분석 시점에 저장만 되고
// 아무도 안 읽던 AiChatMessageIntent를 처음으로 집계해서 보여준다. count가 0이면(대화 이력이
// 없거나 의도가 한 번도 안 잡혔으면) 프론트가 이 칩 자체를 숨긴다.
//
// insight(2026-08-27 추가) - 의도 태그 분포 전체를 OpenAI에 한 번 보내서 만든 자연어 한 줄
// 코멘트. AI 호출이 실패하면 null로 내려가고, 그 경우 프론트는 intentTag/label/count로 조합한
// 기존 템플릿 문구("최근 챗봇에서 'OO'을 가장 많이 물어봤어요")로 안전하게 대체한다.
public record IntentSummaryDto(String intentTag, String label, long count, String insight) {
}

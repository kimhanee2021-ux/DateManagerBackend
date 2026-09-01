package org.ict.datemanagerbackend.domain.calendar.dto.Response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCalendarResponse {
  Long id;
  String title;
  String description;
  LocalDate targetDate;
  Long courseGroupId;
  LocalDateTime createdAt;
  // 아래 3개는 2026-09-01 추가 - MiniCalendar/ListView/UpcomingPanel이 커플 일정(핑크)/개인
  // 일정(검정) 색 구분, 기념일 표시, "내가 만든 것만 삭제 가능" 처리를 하는 데 쓴다.
  Boolean coupleEvent;
  Boolean mine; // 이 응답을 보는 사람이 만든 일정인지(파트너의 커플 일정을 같이 내려줄 때 false)
  Boolean anniversary; // true면 DB에 실제로 없는 가상 항목(N00일 기념일) - id로 삭제 불가
}

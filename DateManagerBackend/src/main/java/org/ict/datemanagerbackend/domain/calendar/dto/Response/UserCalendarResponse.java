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
  // 프론트가 미니 캘린더 점 색깔을 커플(핑크)/개인(검정)으로 구분하는 데 쓴다(2026-08-25 추가).
  boolean coupleEvent;
  // 만난 날짜 기준 100일 단위 기념일 - 실제 DB에 저장된 일정이 아니라 조회 시점에 계산해서
  // 끼워 넣는 가상 항목이라, 이걸로 구분해서 삭제 버튼 등을 안 보여줘야 한다(2026-08-25 추가).
  boolean anniversary;
  // 커플 일정이 파트너에게도 보이게 되면서(coupleEvent), 목록에 남의 일정도 섞여 나올 수 있다.
  // 프론트가 삭제 버튼을 본인 일정에만 보여주도록 조회 시점(뷰어) 기준으로 내려준다(2026-08-25).
  boolean mine;
}

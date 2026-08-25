package org.ict.datemanagerbackend.domain.calendar.dto.Request;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCalendarCreateRequest {
  String title;
  String description;
  LocalDate targetDate;
  Long courseGroupId;
  // "커플 일정으로 등록" 체크박스 값(2026-08-25 추가) - true면 서버가 작성자의 현재 커플 id를
  // 찾아서 붙여준다(프론트가 커플 id를 직접 알 필요 없음).
  boolean coupleEvent;
}

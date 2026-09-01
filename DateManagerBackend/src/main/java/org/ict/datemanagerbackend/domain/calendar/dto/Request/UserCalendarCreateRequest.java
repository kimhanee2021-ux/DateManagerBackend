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
  Boolean coupleEvent;
}

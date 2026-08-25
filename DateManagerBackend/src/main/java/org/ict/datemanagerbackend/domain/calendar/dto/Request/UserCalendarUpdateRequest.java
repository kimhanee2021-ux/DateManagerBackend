package org.ict.datemanagerbackend.domain.calendar.dto.Request;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCalendarUpdateRequest {
  String title;
  String description;
  LocalDate targetDate;
  Long courseGroupId;
  boolean coupleEvent;
}


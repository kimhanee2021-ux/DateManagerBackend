package org.ict.datemanagerbackend.domain.calendar.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor

public class UserCalendarCreateRequest {
  String title;
  String description;
  LocalDate targetDate;
  Long courseGroupId;
}

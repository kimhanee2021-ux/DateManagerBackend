package org.ict.datemanagerbackend.domain.calendar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class UserCalendarResponse {
  Long id;
  String title;
  String description;
  LocalDate targetDate;
  Long courseGroupId;
}

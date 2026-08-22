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
}

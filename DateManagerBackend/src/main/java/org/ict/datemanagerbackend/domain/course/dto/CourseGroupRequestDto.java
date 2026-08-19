package org.ict.datemanagerbackend.domain.course.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CourseGroupRequestDto {
  private Long id;
  private String title;
  private String username;
  private String coupleId;
}

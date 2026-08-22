package org.ict.datemanagerbackend.domain.course.dto;

import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;

import java.time.LocalDateTime;

public record CourseGroupResponse(Long id, String title, LocalDateTime createdAt, Long coupleId) {

  public static CourseGroupResponse from(CourseGroup group) {
    return new CourseGroupResponse(
        group.getId(),
        group.getTitle(),
        group.getCreatedAt(),
        group.getCouple() != null ? group.getCouple().getId() : null
    );
  }
}

package org.ict.datemanagerbackend.domain.course.dto;

import jakarta.validation.constraints.NotBlank;

public record CourseItemMoveRequest(@NotBlank(message = "이동 방향을 지정해주세요") String direction) {
}

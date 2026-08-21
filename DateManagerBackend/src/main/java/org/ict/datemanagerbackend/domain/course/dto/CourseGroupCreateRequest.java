package org.ict.datemanagerbackend.domain.course.dto;

import lombok.*;
import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.user.entity.User;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class CourseGroupCreateRequest {
  private Long userId;
  private Long coupleId;
  private String title;

  public CourseGroup toEntity(User user, Couple couple){


    return CourseGroup.builder().title(this.title).user(user).couple(couple).build();
  }


}

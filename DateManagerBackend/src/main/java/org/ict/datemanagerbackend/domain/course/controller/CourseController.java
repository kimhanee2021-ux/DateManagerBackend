package org.ict.datemanagerbackend.domain.course.controller;

import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.service.CourseService;
import org.ict.datemanagerbackend.swagger.course.CourseApiDocs;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController implements CourseApiDocs {

  private final CourseService courseService;

  @PostMapping("/group")
  public ResponseEntity<String> createCourseGroup(@RequestBody CourseGroupCreateRequest courseGroupCreateRequest) {

    courseService.CreateCourseGroup(courseGroupCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body("코스그룹 생성 완료");
  }

}

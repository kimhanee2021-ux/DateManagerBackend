package org.ict.datemanagerbackend.swagger.course;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name ="코스 CRUD용 API",description = "코스 그룹 입력/수정/삭제/조회 관련 API 문서입니다.")
public interface CourseApiDocs {
  @Operation(summary = "코스 그룹 생성 API" ,description = "인증객체,요청바디를 인자로 받아 생성한 코스그룹 데이터를 dto로 반환합니다")
  public ResponseEntity<CourseGroupResponse> createCourseGroup(Authentication authentication,
                                                               @Valid @RequestBody CourseGroupCreateRequest request);

}

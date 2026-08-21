package org.ict.datemanagerbackend.swagger.course;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name ="코스 CRUD용 API",description = "코스 그룹 입력/수정/삭제/조회 관련 API 문서입니다.")
public interface CourseApiDocs {
  @Operation(summary = "사용자로부터 JSON으로 데이터를 받아 코스 그룹 등록 후 완료 여부를 문자열로 반환합니다.  ")
  public ResponseEntity<String> createCourseGroup(@RequestBody CourseGroupCreateRequest courseGroupCreateRequest);
}

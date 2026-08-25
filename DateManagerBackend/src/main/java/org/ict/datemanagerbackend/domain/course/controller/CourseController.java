package org.ict.datemanagerbackend.domain.course.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseItemMoveRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponse;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponseWithCoords;
import org.ict.datemanagerbackend.domain.course.service.CourseService;
import org.ict.datemanagerbackend.swagger.course.CourseApiDocs;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController implements CourseApiDocs {

  private final CourseService courseService;

  // userId를 요청 body가 아니라 인증 토큰에서 가져오도록 수정(2026-08-22) - 예전엔 body의
  // userId를 그대로 믿어서 아무 유저 id나 넣으면 남의 이름으로 코스그룹이 만들어지는 문제가 있었다.

  //반환값이 모호하여 유효성 검증을 지역 예외처리로 분리
  @PostMapping("/groups")
  public ResponseEntity<CourseGroupResponse> createCourseGroup(Authentication authentication,
                                                               @Valid @RequestBody CourseGroupCreateRequest request) {
    Long userId = (Long) authentication.getPrincipal();
    CourseGroupResponse response = courseService.createCourseGroup(userId, request);
    return ResponseEntity.status(201).body(response);
  }


  //원래라면 전역 예외처리 대상이지만 현재로서는 시간이 부족하여 임시적으로 지역 예외처리
  @ExceptionHandler(NullPointerException.class)
  public ResponseEntity<Map<String, String>> handleNullPointer(NullPointerException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다"));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
    return ResponseEntity.badRequest().body(Map.of("error", message));
  }


  //원래라면
  // 빌더 화면이 "내 코스" 목록을 그리는 데 쓰는 조회 API(2026-08-22 추가) - 방금 만든 코스그룹이
  // 이 목록에 최신순으로 바로 잡혀야 빌더에 뜬다.
  @GetMapping("/groups")
  public ResponseEntity<List<CourseGroupResponseWithCoords>> listMyCourseGroups(Authentication authentication) {

    Long userId = (Long) authentication.getPrincipal();
    List<CourseGroupResponseWithCoords> responses = courseService.listMyCourseGroups(userId);

    return ResponseEntity.ok(responses);
  }

  // 코스에 담긴 장소 순서를 한 칸 위/아래로 옮긴다(빌더 화면 순서 변경 화살표용).
  @PatchMapping("/groups/{groupId}/items/{placeId}/move")
  public ResponseEntity<Void> moveCourseItem(Authentication authentication,
                                              @PathVariable Long groupId,
                                              @PathVariable Long placeId,
                                              @Valid @RequestBody CourseItemMoveRequest request) {
    Long userId = (Long) authentication.getPrincipal();
    courseService.moveCourseItem(userId, groupId, placeId, request.direction());
    return ResponseEntity.noContent().build();
  }

  // 등록한 코스 삭제(2026-08-25 추가) - 빌더 화면 상세보기의 삭제 버튼용.
  @DeleteMapping("/groups/{groupId}")
  public ResponseEntity<Void> deleteCourseGroup(Authentication authentication,
                                                 @PathVariable Long groupId) {
    Long userId = (Long) authentication.getPrincipal();
    courseService.deleteCourseGroup(userId, groupId);
    return ResponseEntity.noContent().build();
  }







}

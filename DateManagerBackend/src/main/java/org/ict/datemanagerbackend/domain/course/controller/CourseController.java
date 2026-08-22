package org.ict.datemanagerbackend.domain.course.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

  private final CourseService courseService;

  // userId를 요청 body가 아니라 인증 토큰에서 가져오도록 수정(2026-08-22) - 예전엔 body의
  // userId를 그대로 믿어서 아무 유저 id나 넣으면 남의 이름으로 코스그룹이 만들어지는 문제가 있었다.
  @PostMapping("/group")
  public ResponseEntity<?> createCourseGroup(Authentication authentication, @RequestBody CourseGroupCreateRequest request) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    if (request.title() == null || request.title().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "코스 제목을 입력해주세요"));
    }
    Long userId = (Long) authentication.getPrincipal();
    return ResponseEntity.status(201).body(courseService.createCourseGroup(userId, request));
  }

  // 빌더 화면이 "내 코스" 목록을 그리는 데 쓰는 조회 API(2026-08-22 추가) - 방금 만든 코스그룹이
  // 이 목록에 최신순으로 바로 잡혀야 빌더에 뜬다.
  @GetMapping("/groups")
  public ResponseEntity<?> listMyCourseGroups(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    Long userId = (Long) authentication.getPrincipal();
    return ResponseEntity.ok(courseService.listMyCourseGroups(userId));
  }

}

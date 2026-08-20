package org.ict.datemanagerbackend.domain.course.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponseDto;
import org.ict.datemanagerbackend.domain.course.dto.CourseMatchSuggestionDto;
import org.ict.datemanagerbackend.domain.course.service.CourseService;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 데이트 코스 빌더(Course Sync Builder) 최소 기능 컨트롤러(2026-08-20).
 * 코스 그룹 CRUD + Step 2(자동 연결/성향 보완 스팟 추천)까지만 제공한다 - Step 3(실시간 정렬)과
 * [현장 라이브 모드]는 이번 범위에서 제외(CourseServiceImpl 클래스 주석 참고).
 */
@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseController {

  private final CourseService courseService;
  private final UserRepository userRepository;

  // AiChatController/CoupleController와 동일한 패턴: JwtAuthFilter가 세팅해둔 principal(userId)로 조회.
  private User currentUser(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return userRepository.findById(userId).orElse(null);
  }

  public record CreateGroupRequest(String title) {
  }

  public record AddItemRequest(Long placeId) {
  }

  /** POST /api/course/groups - 새 코스 그룹 만들기 */
  @PostMapping("/groups")
  public ResponseEntity<?> createGroup(Authentication authentication, @RequestBody CreateGroupRequest request) {
    User me = currentUser(authentication);
    if (me == null) return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    if (request.title() == null || request.title().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "코스 제목을 입력해주세요"));
    }
    return ResponseEntity.ok(courseService.createGroup(me, request.title()));
  }

  /** GET /api/course/groups - 내 코스 그룹 목록(최신순) */
  @GetMapping("/groups")
  public ResponseEntity<List<CourseGroupResponseDto>> listGroups(Authentication authentication) {
    User me = currentUser(authentication);
    if (me == null) return ResponseEntity.status(404).build();
    return ResponseEntity.ok(courseService.listGroups(me));
  }

  /** GET /api/course/groups/{id} - 코스 상세(담긴 장소 타임라인 포함) */
  @GetMapping("/groups/{id}")
  public ResponseEntity<?> getGroup(Authentication authentication, @PathVariable Long id) {
    User me = currentUser(authentication);
    if (me == null) return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    try {
      return ResponseEntity.ok(courseService.getGroup(me, id));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  /** POST /api/course/groups/{id}/items - 코스 맨 뒤에 실제 place 담기(거점 고정도 이걸로 함) */
  @PostMapping("/groups/{id}/items")
  public ResponseEntity<?> addItem(Authentication authentication, @PathVariable Long id,
                                    @RequestBody AddItemRequest request) {
    User me = currentUser(authentication);
    if (me == null) return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    if (request.placeId() == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "placeId가 필요합니다"));
    }
    try {
      return ResponseEntity.ok(courseService.addItem(me, id, request.placeId()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  /** GET /api/course/groups/{id}/auto-match - Step 2: 20분 이내 + 성향 보완 스팟 추천(담기는 별도) */
  @GetMapping("/groups/{id}/auto-match")
  public ResponseEntity<?> autoMatch(Authentication authentication, @PathVariable Long id) {
    User me = currentUser(authentication);
    if (me == null) return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    try {
      List<CourseMatchSuggestionDto> result = courseService.autoMatch(me, id);
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
    }
  }

  /** DELETE /api/course/groups/{id} - 코스 그룹 통째로 삭제 */
  @DeleteMapping("/groups/{id}")
  public ResponseEntity<?> deleteGroup(Authentication authentication, @PathVariable Long id) {
    User me = currentUser(authentication);
    if (me == null) return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    try {
      courseService.deleteGroup(me, id);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  /** DELETE /api/course/groups/{id}/items/{itemId} - 담긴 장소 하나만 빼기 */
  @DeleteMapping("/groups/{id}/items/{itemId}")
  public ResponseEntity<?> removeItem(Authentication authentication, @PathVariable Long id, @PathVariable Long itemId) {
    User me = currentUser(authentication);
    if (me == null) return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    try {
      return ResponseEntity.ok(courseService.removeItem(me, id, itemId));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  public record MoveItemRequest(String direction) {
  }

  /** PATCH /api/course/groups/{id}/items/{itemId}/move - 담긴 장소 순서를 한 칸 앞/뒤로("up"/"down") */
  @PatchMapping("/groups/{id}/items/{itemId}/move")
  public ResponseEntity<?> moveItem(Authentication authentication, @PathVariable Long id, @PathVariable Long itemId,
                                     @RequestBody MoveItemRequest request) {
    User me = currentUser(authentication);
    if (me == null) return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    try {
      return ResponseEntity.ok(courseService.moveItem(me, id, itemId, request.direction()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
    }
  }
}

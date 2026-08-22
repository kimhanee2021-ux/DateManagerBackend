package org.ict.datemanagerbackend.domain.board.controller;

import org.ict.datemanagerbackend.domain.board.dto.Request.BoardPostCreateRequest;
import org.ict.datemanagerbackend.domain.board.dto.Request.BoardPostUpdateRequest;
import org.ict.datemanagerbackend.domain.board.dto.Response.BoardPostResponse;
import org.ict.datemanagerbackend.domain.board.service.BoardPostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// 자유게시판(글 등록/목록/상세/수정/삭제) 컨트롤러. Notice(공지사항, 관리자 전용)와는 별개 기능 -
// 여긴 로그인한 회원 누구나 글을 쓸 수 있고, 수정/삭제는 작성자 본인만 가능하다.
@RestController
@RequestMapping("/api/board")
public class BoardController {

  private final BoardPostService boardPostService;

  public BoardController(BoardPostService boardPostService) {
    this.boardPostService = boardPostService;
  }

  // CoupleController와 동일한 이유: JwtAuthFilter가 검증된 JWT에서 꺼낸 userId(Long)를
  // Authentication.getPrincipal()에 넣어두므로, DB 재조회 없이 바로 꺼내 쓸 수 있다.
  private Long currentUserId(Authentication authentication) {
    return (Long) authentication.getPrincipal();
  }

  @PostMapping
  public ResponseEntity<?> create(Authentication authentication, @RequestBody BoardPostCreateRequest request) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    if (request.getTitle() == null || request.getTitle().isBlank()
        || request.getContent() == null || request.getContent().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "제목과 내용을 입력해주세요"));
    }
    return ResponseEntity.ok(boardPostService.createPost(currentUserId(authentication), request));
  }

  @GetMapping
  public ResponseEntity<List<BoardPostResponse>> getAll() {
    return ResponseEntity.ok(boardPostService.getAllPosts());
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getOne(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(boardPostService.getPost(id));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(Authentication authentication, @PathVariable Long id,
                                   @RequestBody BoardPostUpdateRequest request) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    try {
      return ResponseEntity.ok(boardPostService.updatePost(currentUserId(authentication), id, request));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (SecurityException e) {
      // 작성자 본인이 아닌 사람이 수정을 시도한 경우 - 403(Forbidden): "당신이 누군지는 알지만 권한이 없다"
      return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long id) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    try {
      boardPostService.deletePost(currentUserId(authentication), id);
      return ResponseEntity.ok(Map.of("success", true));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }
  }
}

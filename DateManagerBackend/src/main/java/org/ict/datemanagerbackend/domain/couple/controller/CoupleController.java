package org.ict.datemanagerbackend.domain.couple.controller;

import org.ict.datemanagerbackend.domain.couple.dto.Request.CoupleMetDateRequest;
import org.ict.datemanagerbackend.domain.couple.dto.Response.CoupleStatusResponse;
import org.ict.datemanagerbackend.domain.couple.service.CoupleActionException;
import org.ict.datemanagerbackend.domain.couple.service.CoupleService;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
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

import java.util.Map;

// 커플 초대 링크 생성/수락, 연결 상태 조회, 연결 해제 API. 실제 검증/상태전이 로직은 전부
// CoupleService로 옮겼고(admin/subscription/aichat 도메인과 같은 interface+impl 패턴 통일,
// 2026-08-19), 컨트롤러는 요청 파싱과 서비스가 던진 CoupleActionException을 HTTP 상태코드로
// 매핑하는 것만 담당한다. 전체 상태머신 설명은 CoupleService 클래스 주석 참고.
@RestController
@RequestMapping("/api/couple")
public class CoupleController {

  private final UserRepository userRepository;
  private final CoupleService coupleService;

  public CoupleController(UserRepository userRepository, CoupleService coupleService) {
    this.userRepository = userRepository;
    this.coupleService = coupleService;
  }

  /**
   * Authentication.getPrincipal()에는 JwtAuthFilter가 JWT를 검증하면서 꺼내 넣어둔 "userId(Long)"가
   * 들어있다(SecurityContext에 세팅됨). 즉 이 메서드가 호출되는 시점엔 이미 인증이 끝난 상태이고,
   * principal을 DB에서 실제 User row로 한 번 더 조회해 최신 정보(닉네임 등)를 가져오는 것.
   */
  private User currentUser(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return userRepository.findById(userId).orElse(null);
  }

  @PostMapping("/invite")
  public ResponseEntity<?> createInvite(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    try {
      return ResponseEntity.ok(coupleService.createInvite(me));
    } catch (CoupleActionException e) {
      return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping("/invite/{token}/accept")
  public ResponseEntity<?> acceptInvite(Authentication authentication, @PathVariable String token) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    try {
      Long coupleId = coupleService.acceptInvite(me, token);
      return ResponseEntity.ok(Map.of("success", true, "coupleId", coupleId));
    } catch (CoupleActionException e) {
      return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/status")
  public ResponseEntity<?> getStatus(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    CoupleStatusResponse status = coupleService.getStatus(me);
    return ResponseEntity.ok(status);
  }

  @PutMapping("/met-date")
  public ResponseEntity<?> updateMetDate(Authentication authentication, @RequestBody CoupleMetDateRequest req) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    try {
      coupleService.updateMetDate(me, req.metDate());
      return ResponseEntity.ok(Map.of("success", true, "metDate", req.metDate() == null ? "" : req.metDate().toString()));
    } catch (CoupleActionException e) {
      return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
  }

  @DeleteMapping
  public ResponseEntity<?> disconnect(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    try {
      coupleService.disconnect(me);
      return ResponseEntity.ok(Map.of("success", true));
    } catch (CoupleActionException e) {
      return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/notifications/unread")
  public ResponseEntity<?> getUnreadNotifications(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    return ResponseEntity.ok(coupleService.getUnreadNotifications(me));
  }

  @PostMapping("/notifications/read-all")
  public ResponseEntity<?> markAllNotificationsRead(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    coupleService.markAllNotificationsRead(me);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @PostMapping("/ack-admin-reconnect")
  public ResponseEntity<?> ackAdminReconnect(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    try {
      coupleService.ackAdminReconnect(me);
      return ResponseEntity.ok(Map.of("success", true));
    } catch (CoupleActionException e) {
      return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
  }
}

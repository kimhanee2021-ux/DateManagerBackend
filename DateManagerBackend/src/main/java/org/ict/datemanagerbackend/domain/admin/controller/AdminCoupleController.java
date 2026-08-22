package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateCoupleRequest;
import org.ict.datemanagerbackend.domain.admin.service.AdminAuthService;
import org.ict.datemanagerbackend.domain.admin.service.AdminCoupleService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/admin/couples")
public class AdminCoupleController {

  private final AdminAuthService adminAuthService;
  private final AdminCoupleService adminCoupleService;

  public AdminCoupleController(AdminAuthService adminAuthService, AdminCoupleService adminCoupleService) {
    this.adminAuthService = adminAuthService;
    this.adminCoupleService = adminCoupleService;
  }

  // filter: all(기본) / subscribed(멤버 중 구독자 있는 커플) / free(멤버 전원 비구독) - 구독 여부 기준
  // status: active(기본) / all - 연결 상태 기준. 기본은 해제된 예전 커플을 안 보여주되,
  // 관리자가 이력까지 보고 싶을 때는 status=all로 명시적으로 요청하게 한다.
  @GetMapping
  public ResponseEntity<?> listCouples(Authentication authentication,
                                        @RequestParam(required = false, defaultValue = "all") String filter,
                                        @RequestParam(required = false, defaultValue = "active") String status) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminCoupleService.listCouples(filter, status));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateCouple(Authentication authentication, @PathVariable Long id,
                                         @RequestBody AdminUpdateCoupleRequest req) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    try {
      return ResponseEntity.ok(adminCoupleService.updateCouple(id, req));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteCouple(Authentication authentication, @PathVariable Long id) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    try {
      adminCoupleService.deleteCouple(id);
      return ResponseEntity.ok(Map.of("success", true));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).body(Map.of("error", "이 커플은 기념일·채팅 등 연결된 데이터가 있어 삭제할 수 없습니다"));
    }
  }
}

package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateUserRequest;
import org.ict.datemanagerbackend.domain.admin.service.AdminAuthService;
import org.ict.datemanagerbackend.domain.admin.service.AdminUserService;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final AdminAuthService adminAuthService;
  private final AdminUserService adminUserService;

  public AdminUserController(AdminAuthService adminAuthService, AdminUserService adminUserService) {
    this.adminAuthService = adminAuthService;
    this.adminUserService = adminUserService;
  }

  // 가입일 내림차순 15명씩 페이지네이션 + 이메일/닉네임 검색 + 일반/구독회원 필터. 탈퇴 회원은 항상 제외.
  @GetMapping
  public ResponseEntity<?> listUsers(Authentication authentication,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(required = false, defaultValue = "email") String field,
                                      @RequestParam(required = false, defaultValue = "all") String filter,
                                      @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminUserService.listUsers(search, field, filter, pageable));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateUser(Authentication authentication, @PathVariable Long id,
                                       @RequestBody AdminUpdateUserRequest req) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    try {
      return ResponseEntity.ok(adminUserService.updateUser(id, req));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteUser(Authentication authentication, @PathVariable Long id) {
    User me = adminAuthService.currentUser(authentication);
    if (!adminAuthService.isAdmin(me)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    try {
      adminUserService.withdrawUser(me.getId(), id);
      return ResponseEntity.ok(Map.of("success", true));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}

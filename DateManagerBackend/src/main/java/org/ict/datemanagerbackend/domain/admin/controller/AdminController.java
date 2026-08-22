package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.admin.service.AdminAuthService;
import org.ict.datemanagerbackend.domain.admin.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 관리자 로그인 여부 확인 + 홈 대시보드 통계. 유저/커플/신고/장소는 각각
// AdminUserController/AdminCoupleController/AdminReportController/AdminPlaceController로 분리했다
// (2026-08-18 - 원래 이 컨트롤러 하나에 다 몰려 있었음).
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AdminAuthService adminAuthService;
  private final AdminDashboardService adminDashboardService;

  public AdminController(AdminAuthService adminAuthService, AdminDashboardService adminDashboardService) {
    this.adminAuthService = adminAuthService;
    this.adminDashboardService = adminDashboardService;
  }

  @GetMapping("/check")
  public ResponseEntity<?> check(Authentication authentication) {
    return ResponseEntity.ok(Map.of("isAdmin", adminAuthService.isAdmin(authentication)));
  }

  @GetMapping("/dashboard")
  public ResponseEntity<?> dashboard(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminDashboardService.getStats());
  }
}

package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateReportRequest;
import org.ict.datemanagerbackend.domain.admin.service.AdminAuthService;
import org.ict.datemanagerbackend.domain.admin.service.AdminReportService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/admin/reports")
public class AdminReportController {

  private final AdminAuthService adminAuthService;
  private final AdminReportService adminReportService;

  public AdminReportController(AdminAuthService adminAuthService, AdminReportService adminReportService) {
    this.adminAuthService = adminAuthService;
    this.adminReportService = adminReportService;
  }

  @GetMapping
  public ResponseEntity<?> listReports(Authentication authentication,
                                        @RequestParam(required = false) String status,
                                        @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminReportService.listReports(status, pageable));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateReportStatus(Authentication authentication, @PathVariable Long id,
                                               @RequestBody AdminUpdateReportRequest req) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    try {
      return ResponseEntity.ok(adminReportService.updateStatus(id, req));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}

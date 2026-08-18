package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.admin.service.AdminAuthService;
import org.ict.datemanagerbackend.domain.admin.service.AdminPlaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 장소 데이터 동기화 트리거/백업(CSV)/현황 조회. AdminController에 같이 있던 걸 분리했다(2026-08-18).
@RestController
@RequestMapping("/api/admin/places")
public class AdminPlaceController {

  private final AdminAuthService adminAuthService;
  private final AdminPlaceService adminPlaceService;

  public AdminPlaceController(AdminAuthService adminAuthService, AdminPlaceService adminPlaceService) {
    this.adminAuthService = adminAuthService;
    this.adminPlaceService = adminPlaceService;
  }

  // 장소 데이터 동기화는 원래 매일 새벽에 자동(@Scheduled)으로만 도는데, 개발 중 수동으로
  // 바로 실행해서 결과를 확인하고 싶을 때 쓰는 관리자 전용 트리거. 외부 API를 여러 번
  // 호출하느라 응답이 오래 걸릴 수 있어(수십 초~수 분) 동기 방식으로 그대로 기다린다.
  @PostMapping("/sync/{source}")
  public ResponseEntity<?> triggerPlaceSync(Authentication authentication, @PathVariable String source) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    try {
      adminPlaceService.syncSource(source);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(502).body(Map.of("error", "동기화 중 오류가 발생했습니다: " + e.getMessage()));
    }
    return ResponseEntity.ok(adminPlaceService.getSyncStatus());
  }

  // 블랙리스트 필터 도입(2026-08-14) 이전에 이미 저장된 프랜차이즈/체인점(올리브영 등)을 한 번에 정리.
  @PostMapping("/cleanup-blacklist")
  public ResponseEntity<?> cleanupBlacklistedPlaces(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.cleanupBlacklistedPlaces());
  }

  @GetMapping("/export")
  public ResponseEntity<String> exportPlaces(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body("관리자만 접근할 수 있습니다");
    }
    return ResponseEntity.ok()
        .header("Content-Type", "text/csv; charset=UTF-8")
        .header("Content-Disposition", "attachment; filename=places_export.csv")
        .body(adminPlaceService.exportPlacesCsv());
  }

  @GetMapping("/categories/export")
  public ResponseEntity<String> exportPlaceCategories(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body("관리자만 접근할 수 있습니다");
    }
    return ResponseEntity.ok()
        .header("Content-Type", "text/csv; charset=UTF-8")
        .header("Content-Disposition", "attachment; filename=place_categories_export.csv")
        .body(adminPlaceService.exportPlaceCategoriesCsv());
  }

  @GetMapping("/sync-status")
  public ResponseEntity<?> placesSyncStatusEndpoint(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.getSyncStatus());
  }
}

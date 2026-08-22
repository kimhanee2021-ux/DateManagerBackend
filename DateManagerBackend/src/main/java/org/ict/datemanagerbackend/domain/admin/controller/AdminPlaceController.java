package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.admin.service.AdminAuthService;
import org.ict.datemanagerbackend.domain.admin.service.AdminPlaceService;
import org.ict.datemanagerbackend.domain.place.dto.PlaceDumpDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

  // dedup 반경을 50m -> 200~280m로 넓히기 전에 이미 저장된 중복(같은 이름, 소스마다 좌표가 조금씩
  // 다른 경우) 정리. 2026-08-20.
  @PostMapping("/merge-duplicates")
  public ResponseEntity<?> mergeDuplicatePlaces(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.mergeDuplicatePlaces());
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

  // /categories/export로 받은 CSV를 그대로 body에 넣어 올리면 코드 pull/재시작 없이도 성향점수가
  // 바로 반영된다(2026-08-20, 팀원 공유용). Content-Type은 text/plain으로 받는다 - 팀원이 curl -d
  // @file.csv 등으로 바로 올릴 수 있게 하기 위해 multipart 대신 raw body로 받는다.
  @PostMapping(value = "/categories/import", consumes = "text/plain")
  public ResponseEntity<?> importPlaceCategories(Authentication authentication, @RequestBody String csv) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.importPlaceCategoriesCsv(csv));
  }

  // 팀원 공유용 place 전체 백업(JSON). 이 응답을 그대로 파일로 저장해뒀다가 다른 팀원 DB에
  // /full-import로 보내면 place_styles/place_realities/place_amenities까지 그대로 재현된다(2026-08-20).
  @GetMapping("/full-export")
  public ResponseEntity<?> exportFullDump(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.exportFullDump());
  }

  // /full-export로 받은 JSON을 그대로 body에 넣어 호출하면 내 DB에 반영된다. 외부 API 키가 하나도
  // 없어도(KOPIS/카카오/네이버/TourAPI 등) place 데이터를 통째로 받을 수 있는 방법.
  @PostMapping("/full-import")
  public ResponseEntity<?> importFullDump(Authentication authentication, @RequestBody List<PlaceDumpDto> dump) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.importFullDump(dump));
  }

  // "홍원" 좌표 오차(2026-08-20) 발견 후 TourAPI 출처 전체를 카카오 주소 검색으로 재검증하는
  // 배치. 오래 걸려서(수십 분~1시간대) 백그라운드로 시작만 시키고 바로 응답한다.
  @PostMapping("/verify-tourapi-coordinates")
  public ResponseEntity<?> verifyTourApiCoordinates(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.verifyTourApiCoordinates());
  }

  // 이름 키워드로 안 잡히는 미분류 장소를 네이버 지역 검색 API로 재분류(2026-08-22) - API 호출량이
  // 커서(장소 하나당 1회) limit으로 배치 크기를 조절한다. 여러 번 나눠 호출해도 안전(이미 연결된
  // 장소는 자동으로 대상에서 빠짐).
  @PostMapping("/categories/naver-match")
  public ResponseEntity<?> matchPlaceCategoriesViaNaver(Authentication authentication,
                                                          @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.matchPlaceCategoriesViaNaver(limit));
  }

  @GetMapping("/categories/links/export")
  public ResponseEntity<String> exportPlaceCategoryLinks(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body("관리자만 접근할 수 있습니다");
    }
    return ResponseEntity.ok()
        .header("Content-Type", "text/csv; charset=UTF-8")
        .header("Content-Disposition", "attachment; filename=place_category_links_export.csv")
        .body(adminPlaceService.exportPlaceCategoryLinksCsv());
  }

  @PostMapping(value = "/categories/links/import", consumes = "text/plain")
  public ResponseEntity<?> importPlaceCategoryLinks(Authentication authentication, @RequestBody String csv) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.importPlaceCategoryLinksCsv(csv));
  }

  @GetMapping("/sync-status")
  public ResponseEntity<?> placesSyncStatusEndpoint(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    return ResponseEntity.ok(adminPlaceService.getSyncStatus());
  }
}

package org.ict.datemanagerbackend.domain.financialplanner.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.admin.service.AdminAuthService;
import org.ict.datemanagerbackend.domain.financialplanner.service.ExchangeRateSyncService;
import org.ict.datemanagerbackend.domain.financialplanner.service.FinlifeSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// finlife/환율 동기화는 원래 매일 새벽·매시 자동(@Scheduled)으로만 도는데, 개발 중 수동으로 바로
// 실행해서 결과를 확인하고 싶을 때 쓰는 관리자 전용 트리거(AdminPlaceController의 sync/{source}와
// 동일한 목적, 2026-08-31).
@RestController
@RequestMapping("/api/admin/financial")
@RequiredArgsConstructor
public class AdminFinancialController {

  private final AdminAuthService adminAuthService;
  private final FinlifeSyncService finlifeSyncService;
  private final ExchangeRateSyncService exchangeRateSyncService;

  @PostMapping("/sync/finlife")
  public ResponseEntity<?> triggerFinlifeSync(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    try {
      finlifeSyncService.syncProducts();
      return ResponseEntity.ok(Map.of("status", "완료"));
    } catch (Exception e) {
      return ResponseEntity.status(502).body(Map.of("error", "동기화 중 오류가 발생했습니다: " + e.getMessage()));
    }
  }

  @PostMapping("/sync/exchange-rate")
  public ResponseEntity<?> triggerExchangeRateSync(Authentication authentication) {
    if (!adminAuthService.isAdmin(authentication)) {
      return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
    }
    try {
      exchangeRateSyncService.syncRates();
      return ResponseEntity.ok(Map.of("status", "완료"));
    } catch (Exception e) {
      return ResponseEntity.status(502).body(Map.of("error", "동기화 중 오류가 발생했습니다: " + e.getMessage()));
    }
  }
}

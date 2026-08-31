package org.ict.datemanagerbackend.domain.financialplanner.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.financialplanner.dto.DashboardResponseDto;
import org.ict.datemanagerbackend.domain.financialplanner.dto.GoalRequestDto;
import org.ict.datemanagerbackend.domain.financialplanner.dto.GoalResultDto;
import org.ict.datemanagerbackend.domain.financialplanner.dto.ProgressUpdateRequestDto;
import org.ict.datemanagerbackend.domain.financialplanner.service.FinancialPlannerService;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// AI 스마트 자금 플래너 API(2026-08-31, 개발 명세서 3-5). AiChatController와 동일한 JWT 인증
// 패턴(Authentication.getPrincipal() = userId)을 그대로 쓴다.
@RestController
@RequestMapping("/api/v1/planner")
@RequiredArgsConstructor
public class FinancialPlannerController {

  private final FinancialPlannerService financialPlannerService;
  private final UserRepository userRepository;

  private User currentUser(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return userRepository.findById(userId).orElse(null);
  }

  @PostMapping("/goal")
  public ResponseEntity<?> createOrUpdateGoal(Authentication authentication, @RequestBody GoalRequestDto request) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User user = currentUser(authentication);
    if (user == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    if (request.text() == null || request.text().isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "목표 내용을 입력해주세요"));
    }
    GoalResultDto result = financialPlannerService.createOrUpdateGoal(user, request.text());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/dashboard")
  public ResponseEntity<?> getDashboard(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User user = currentUser(authentication);
    if (user == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    DashboardResponseDto result = financialPlannerService.getDashboard(user);
    return ResponseEntity.ok(result);
  }

  @PatchMapping("/goal/{goalId}/progress")
  public ResponseEntity<?> updateProgress(Authentication authentication, @PathVariable Long goalId,
                                           @RequestBody ProgressUpdateRequestDto request) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    User user = currentUser(authentication);
    if (user == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    if (request.currentAmount() == null || request.currentAmount() < 0) {
      return ResponseEntity.badRequest().body(Map.of("error", "올바른 금액을 입력해주세요"));
    }
    try {
      GoalResultDto result = financialPlannerService.updateProgress(user, goalId, request.currentAmount());
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }
}

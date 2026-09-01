package org.ict.datemanagerbackend.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.user.dto.SaveOnboardingStyleRequest;
import org.ict.datemanagerbackend.domain.user.service.UserStyleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me/styles")
@RequiredArgsConstructor
public class UserStyleController {

  private final UserStyleService userStyleService;

  @PutMapping("/onboarding")
  public ResponseEntity<?> saveOnboarding(
      Authentication authentication,
      @RequestBody SaveOnboardingStyleRequest request
  ) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    Long userId = (Long) authentication.getPrincipal();
    userStyleService.saveOnboarding(userId, request);
    return ResponseEntity.noContent().build();
  }

  // 저장된 성향값 조회(2026-08-20) - 로그인 시 프론트가 이걸 불러와 personaProfile을 채운다.
  // 아직 온보딩을 저장한 적 없는 유저는 204로 응답한다.
  @GetMapping
  public ResponseEntity<?> getMyStyle(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    Long userId = (Long) authentication.getPrincipal();
    SaveOnboardingStyleRequest style = userStyleService.getMyStyle(userId);
    return style == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(style);
  }

  // 커플 화면 "성향 궁합"용(2026-09-01 추가) - 연결된 파트너가 없거나 파트너가 온보딩을 저장한
  // 적 없으면 204(빈 응답). 파트너 실제 점수를 항상 중립값(50)으로만 표시하던 문제 수정용.
  @GetMapping("/partner")
  public ResponseEntity<?> getPartnerStyle(Authentication authentication) {
    if (authentication == null) {
      return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
    }
    Long userId = (Long) authentication.getPrincipal();
    SaveOnboardingStyleRequest style = userStyleService.getPartnerStyle(userId);
    return style == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(style);
  }
}

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

@RestController
@RequestMapping("/api/me/styles")
@RequiredArgsConstructor
public class UserStyleController {

  private final UserStyleService userStyleService;

  @PutMapping("/onboarding")
  public ResponseEntity<Void> saveOnboarding(
      Authentication authentication,
      @RequestBody SaveOnboardingStyleRequest request
  ) {
    Long userId = (Long) authentication.getPrincipal();
    userStyleService.saveOnboarding(userId, request);
    return ResponseEntity.noContent().build();
  }

  // 저장된 성향값 조회(2026-08-20) - 로그인 시 프론트가 이걸 불러와 personaProfile을 채운다.
  // 아직 온보딩을 저장한 적 없는 유저는 204로 응답한다.
  @GetMapping
  public ResponseEntity<SaveOnboardingStyleRequest> getMyStyle(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    SaveOnboardingStyleRequest style = userStyleService.getMyStyle(userId);
    return style == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(style);
  }
}

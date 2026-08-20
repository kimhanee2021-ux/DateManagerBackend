package org.ict.datemanagerbackend.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.user.dto.SaveOnboardingStyleRequest;
import org.ict.datemanagerbackend.domain.user.service.UserStyleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
}

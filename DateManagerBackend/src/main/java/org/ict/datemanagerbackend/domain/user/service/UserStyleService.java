package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.user.dto.SaveOnboardingStyleRequest;

/**
 * 유저 성향 저장 기능의 공개 계약입니다.
 */
public interface UserStyleService {

  void saveOnboarding(Long userId, SaveOnboardingStyleRequest request);
}

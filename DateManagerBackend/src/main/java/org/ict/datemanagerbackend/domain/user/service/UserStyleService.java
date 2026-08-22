package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.user.dto.SaveOnboardingStyleRequest;

/**
 * 유저 성향 저장 기능의 공개 계약입니다.
 */
public interface UserStyleService {

  void saveOnboarding(Long userId, SaveOnboardingStyleRequest request);

  // <<저장된 성향값 조회 - 아직 온보딩을 저장한 적 없으면 null>>
  SaveOnboardingStyleRequest getMyStyle(Long userId);
}

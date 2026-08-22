package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.user.dto.SaveOnboardingStyleRequest;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.entity.UserStyle;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserStyleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UserStyleServiceImpl implements UserStyleService {

  private final UserRepository userRepository;
  private final UserStyleRepository userStyleRepository;

  public UserStyleServiceImpl(
      UserRepository userRepository,
      UserStyleRepository userStyleRepository
  ) {
    this.userRepository = userRepository;
    this.userStyleRepository = userStyleRepository;
  }

  // USER_STYLES.USER_ID가 PK이므로 기존 행이 있으면 갱신하고, 없을 때만 한 번 생성한다.
  @Override
  @Transactional
  public void saveOnboarding(Long userId, SaveOnboardingStyleRequest request) {
    validate(request);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

    UserStyle userStyle = userStyleRepository.findById(userId)
        .orElseGet(() -> UserStyle.create(user));

    userStyle.applyOnboarding(
        request.energy(),
        request.immersion(),
        request.vibe(),
        request.aesthetic(),
        request.pacing(),
        request.depth()
    );

    userStyleRepository.save(userStyle);
  }

  // 로그인 시 프론트가 저장된 성향값을 불러와 personaProfile을 채우는 용도(2026-08-20) - 그전까진
  // 온보딩 화면을 그 자리에서 완료할 때만 메모리에 잠깐 채워지고 새로고침/재로그인하면 사라졌었다.
  @Override
  public SaveOnboardingStyleRequest getMyStyle(Long userId) {
    return userStyleRepository.findById(userId)
        .map(style -> new SaveOnboardingStyleRequest(
            style.getInitEnergy(),
            style.getInitImmersion(),
            style.getInitVibe(),
            style.getInitAesthetic(),
            style.getInitPacing(),
            style.getInitDepth()
        ))
        .orElse(null);
  }

  private void validate(SaveOnboardingStyleRequest request) {
    if (request == null) {
      throw badRequest("온보딩 점수가 필요합니다.");
    }
    validateScore("energy", request.energy());
    validateScore("immersion", request.immersion());
    validateScore("vibe", request.vibe());
    validateScore("aesthetic", request.aesthetic());
    validateScore("pacing", request.pacing());
    validateScore("depth", request.depth());
  }

  private void validateScore(String axis, Integer score) {
    if (score == null || score < 0 || score > 100) {
      throw badRequest(axis + " 점수는 0~100 사이여야 합니다.");
    }
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}

package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
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
  private final CoupleMemberRepository coupleMemberRepository;

  public UserStyleServiceImpl(
      UserRepository userRepository,
      UserStyleRepository userStyleRepository,
      CoupleMemberRepository coupleMemberRepository
  ) {
    this.userRepository = userRepository;
    this.userStyleRepository = userStyleRepository;
    this.coupleMemberRepository = coupleMemberRepository;
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
    // 2026-08-22 - UserStyle 내부 저장값은 실시간 갱신 엔진 때문에 소수점(Double)이지만, 이
    // API로 나가는 값은 예전처럼 정수 그대로 유지한다(UserStyle.round() 참고).
    return userStyleRepository.findById(userId)
        .map(style -> new SaveOnboardingStyleRequest(
            UserStyle.round(style.getInitEnergy()),
            UserStyle.round(style.getInitImmersion()),
            UserStyle.round(style.getInitVibe()),
            UserStyle.round(style.getInitAesthetic()),
            UserStyle.round(style.getInitPacing()),
            UserStyle.round(style.getInitDepth())
        ))
        .orElse(null);
  }

  // 커플 화면 "성향 궁합"의 파트너 점수 - 여태껏 UserStyle API가 없어서 항상 중립값(50)으로만
  // 표시되던 걸 실제 조회로 채운다(2026-09-01). 연결된 커플이 없거나 파트너가 온보딩을 저장한
  // 적 없으면(온보딩 안 함) null - 화면에서 getMyStyle과 같은 방식으로 "아직 없음" 처리한다.
  @Override
  public SaveOnboardingStyleRequest getPartnerStyle(Long userId) {
    Long partnerId = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(userId)
        .map(member -> member.getCouple().getId())
        .flatMap(coupleId -> coupleMemberRepository.findByCoupleId(coupleId).stream()
            .filter(member -> member.getLeftAt() == null && !member.getUser().getId().equals(userId))
            .findFirst())
        .map(member -> member.getUser().getId())
        .orElse(null);

    return partnerId == null ? null : getMyStyle(partnerId);
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

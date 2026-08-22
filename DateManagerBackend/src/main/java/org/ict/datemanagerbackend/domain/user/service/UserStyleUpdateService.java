package org.ict.datemanagerbackend.domain.user.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.entity.UserActivityLog;
import org.ict.datemanagerbackend.domain.user.entity.UserStyle;
import org.ict.datemanagerbackend.domain.user.repository.UserActivityLogRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserStyleRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 온보딩 이후, 유저의 행동(장소 열람/AI챗 발화 등)에 따라 UserStyle(성향값)을 조금씩 갱신하는 엔진.
 * "유저 성향 점수(user_styles) 실시간 갱신 로직" 스펙(2026-08-08, 핸드오프 문서)을 그대로 구현했다.
 * backup/user-style 브랜치(2026-08-18)에서 포팅 - 그때는 팀원의 실제 UserStyle 엔티티가 아직
 * main에 없어서 예비용으로만 남아 있었는데, 두 작업이 같은 스펙 문서를 봐서 축 이름(ENERGY 등)과
 * UserStyle 필드명(initEnergy 등)이 그대로 일치해 포팅에 별다른 손이 안 갔다(2026-08-22).
 *
 * <p>핵심 공식(스펙 §1):
 * <pre>
 *   delta_raw   = α(activity_type) × (targetAxisScore − currentScore)
 *   delta_final = clamp(delta_raw, −3, +3)                 // 한 번에 너무 크게 안 움직이게 상한선
 *   newScore    = round(clamp(currentScore + delta_final, 0, 100))
 * </pre>
 * α는 행동이 "얼마나 확실한 의사표현인지"에 따라 다르다(스펙 §2) - 약한 신호(열람)일수록 작고,
 * 강한 신호(코스 담기, AI챗 명시적 언급)일수록 크다. ±3 상한선 덕분에 한 번의 튀는 클릭에 점수가
 * 과하게 반응하지 않고, 반복된 행동만 점수를 서서히 움직인다(스펙 §3 시뮬레이션 참고).
 */
@Service
@RequiredArgsConstructor
public class UserStyleUpdateService {

  // 스펙 §2 - 신호 강도별 학습률. 실제 서비스에 붙여보고 체감상 너무 빠르면/느리면 조정할 초안 값.
  public static final double ALPHA_VIEW = 0.02;   // 약함: 상세페이지 열람/스크롤
  public static final double ALPHA_LIKE = 0.05;   // 보통: 좋아요/찜
  public static final double ALPHA_STRONG = 0.10; // 강함: 코스에 담기 / AI챗 명시적 언급

  private static final double DELTA_CAP = 3.0;

  private final UserRepository userRepository;
  private final UserStyleRepository userStyleRepository;
  private final UserActivityLogRepository userActivityLogRepository;

  /**
   * 장소 상세페이지 열람 트리거(스펙 §2 "약함"). 장소가 가진 5개 성향 축 중 |점수−50|이 큰
   * 순서로 상위 1~2개(가장 특징적인 축)만 골라 반영한다(스펙 §1 "적용 대상 축") - 모든 축을 매번
   * 건드리면 "왜 좋아했는지"와 무관한 노이즈가 계속 쌓이기 때문.
   */
  public void applyPlaceView(Long userId, PlaceStyle placeStyle) {
    applyTopAxesFromPlace(userId, "PLACE_VIEW", ALPHA_VIEW, placeStyle);
  }

  /** 좋아요/찜 트리거(스펙 §2 "보통"). 좋아요 기능이 아직 없어 지금은 호출부가 없다. */
  public void applyPlaceLike(Long userId, PlaceStyle placeStyle) {
    applyTopAxesFromPlace(userId, "PLACE_LIKE", ALPHA_LIKE, placeStyle);
  }

  /** 코스에 담기 트리거(스펙 §2 "강함"). 코스빌더 백엔드가 아직 없어 지금은 호출부가 없다. */
  public void applyCourseAdd(Long userId, PlaceStyle placeStyle) {
    applyTopAxesFromPlace(userId, "COURSE_ADD", ALPHA_STRONG, placeStyle);
  }

  /**
   * AI챗 명시적 언급 트리거(스펙 §2 "강함"). AiChatService가 메시지 하나를 분석할 때 이미 축(scoreType)과
   * 목표값(scoreValue)을 GPT가 직접 뽑아주므로, 장소처럼 "상위 축을 고르는" 과정 없이 그 값을 그대로 쓴다.
   */
  public void applyAiChatMention(Long userId, String axis, int targetScore) {
    applyActivity(userId, "AI_CHAT_MENTION", ALPHA_STRONG, axis, targetScore);
  }

  private void applyTopAxesFromPlace(Long userId, String activityType, double alpha, PlaceStyle placeStyle) {
    Map<String, Integer> axisScores = Map.of(
        "ENERGY", placeStyle.getScoreEnergy(),
        "IMMERSION", placeStyle.getScoreImmersion(),
        "VIBE", placeStyle.getScoreVibe(),
        "AESTHETIC", placeStyle.getScoreAesthetic(),
        "DEPTH", placeStyle.getScoreDepth()
    );
    axisScores.entrySet().stream()
        .sorted((a, b) -> Integer.compare(Math.abs(b.getValue() - 50), Math.abs(a.getValue() - 50)))
        .limit(2)
        .forEach(entry -> applyActivity(userId, activityType, alpha, entry.getKey(), entry.getValue()));
  }

  @Transactional
  public void applyActivity(Long userId, String activityType, double alpha, String axis, int targetScore) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NoSuchElementException("사용자 아이디가 없습니다."));
    UserStyle style = userStyleRepository.findById(userId)
        .orElseGet(() -> UserStyle.create(user));

    // 2026-08-22 - currentScore를 Double로 유지해서 소수점 진행분이 매번 안 사라지게 한다
    // (예전엔 Integer로 저장해서, delta가 0.5 미만인 축은 반올림 시 매번 0이 되어 영원히 안
    // 움직이는 문제가 실측으로 확인됐다).
    Double existingScore = style.getAxisScore(axis);
    double currentScore = existingScore != null ? existingScore : 50.0; // 아직 온보딩 전이면 중립값 50에서 시작
    double deltaRaw = alpha * (targetScore - currentScore);
    double deltaFinal = Math.max(-DELTA_CAP, Math.min(DELTA_CAP, deltaRaw));
    double newScore = clamp(currentScore + deltaFinal, 0, 100);

    style.setAxisScore(axis, newScore);
    userStyleRepository.save(style);

    // 원본 로그 insert + UserStyle update를 같은 트랜잭션으로 커밋한다(스펙 §5 4단계).
    userActivityLogRepository.save(UserActivityLog.builder()
        .user(user)
        .activityType(activityType)
        .targetMatrix(axis)
        .scoreImpact((int) Math.round(deltaFinal))
        .build());
  }

  private double clamp(double v, double lo, double hi) {
    return Math.max(lo, Math.min(hi, v));
  }
}

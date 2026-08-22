package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.user.type.ActivityType;

import java.math.BigDecimal;
import java.math.RoundingMode;

// DB나 특정 성향 축을 모르는 순수 계산기. 장소 축 선정과 USER_STYLES 갱신은 이후
// UserActivityService가 담당하고, 이 클래스는 전달받은 한 축의 점수만 계산한다.
public class UserStyleCalculator {

  private static final int SCORE_SCALE = 2;
  private static final int RAW_DELTA_SCALE = 4;
  private static final BigDecimal MIN_SCORE = new BigDecimal("0.00");
  private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
  private static final BigDecimal MAX_DELTA = new BigDecimal("3.00");
  private static final BigDecimal MIN_DELTA = MAX_DELTA.negate();

  public ScoreChange calculate(int currentScore, int targetScore, ActivityType activityType) {
    return calculate(
        BigDecimal.valueOf(currentScore),
        BigDecimal.valueOf(targetScore),
        activityType
    );
  }

  public ScoreChange calculate(
      BigDecimal currentScore,
      BigDecimal targetScore,
      ActivityType activityType
  ) {
    if (activityType == null) {
      throw new IllegalArgumentException("activityType은 필수입니다.");
    }

    BigDecimal before = validateAndNormalize("currentScore", currentScore);
    BigDecimal target = validateAndNormalize("targetScore", targetScore);

    BigDecimal rawDelta = target.subtract(before)
        .multiply(activityType.getAlpha())
        .setScale(RAW_DELTA_SCALE, RoundingMode.HALF_UP);

    BigDecimal cappedDelta = clamp(rawDelta, MIN_DELTA, MAX_DELTA);
    BigDecimal newScore = clamp(before.add(cappedDelta), MIN_SCORE, MAX_SCORE)
        .setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    BigDecimal appliedDelta = newScore.subtract(before)
        .setScale(SCORE_SCALE, RoundingMode.HALF_UP);

    return new ScoreChange(before, target, rawDelta, appliedDelta, newScore);
  }

  private BigDecimal validateAndNormalize(String name, BigDecimal score) {
    if (score == null) {
      throw new IllegalArgumentException(name + "는 필수입니다.");
    }
    if (score.compareTo(MIN_SCORE) < 0 || score.compareTo(MAX_SCORE) > 0) {
      throw new IllegalArgumentException(name + "는 0~100 사이여야 합니다.");
    }
    return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
    if (value.compareTo(min) < 0) {
      return min;
    }
    if (value.compareTo(max) > 0) {
      return max;
    }
    return value;
  }

  public record ScoreChange(
      BigDecimal beforeScore,
      BigDecimal targetScore,
      BigDecimal rawDelta,
      BigDecimal appliedDelta,
      BigDecimal newScore
  ) {
  }
}

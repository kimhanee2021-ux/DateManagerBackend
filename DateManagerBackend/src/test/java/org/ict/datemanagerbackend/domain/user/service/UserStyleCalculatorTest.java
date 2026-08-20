package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.user.type.ActivityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserStyleCalculatorTest {

  private UserStyleCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new UserStyleCalculator();
  }

  @Test
  void detailViewKeepsFractionalScoreInsteadOfRoundingToInteger() {
    UserStyleCalculator.ScoreChange result =
        calculator.calculate(50, 90, ActivityType.VIEW_DETAIL);

    assertEquals(new BigDecimal("0.8000"), result.rawDelta());
    assertEquals(new BigDecimal("0.80"), result.appliedDelta());
    assertEquals(new BigDecimal("50.80"), result.newScore());
  }

  @Test
  void likeUsesItsConfiguredLearningRate() {
    UserStyleCalculator.ScoreChange result =
        calculator.calculate(50, 90, ActivityType.LIKE);

    assertEquals(new BigDecimal("2.0000"), result.rawDelta());
    assertEquals(new BigDecimal("52.00"), result.newScore());
  }

  @Test
  void strongPositiveSignalIsCappedAtThreePoints() {
    UserStyleCalculator.ScoreChange result =
        calculator.calculate(50, 90, ActivityType.ADD_TO_COURSE);

    assertEquals(new BigDecimal("4.0000"), result.rawDelta());
    assertEquals(new BigDecimal("3.00"), result.appliedDelta());
    assertEquals(new BigDecimal("53.00"), result.newScore());
  }

  @Test
  void strongNegativeSignalIsCappedAtMinusThreePoints() {
    UserStyleCalculator.ScoreChange result =
        calculator.calculate(90, 20, ActivityType.ADD_TO_COURSE);

    assertEquals(new BigDecimal("-7.0000"), result.rawDelta());
    assertEquals(new BigDecimal("-3.00"), result.appliedDelta());
    assertEquals(new BigDecimal("87.00"), result.newScore());
  }

  @Test
  void repeatedViewsConvergeWithoutReachingTheTargetImmediately() {
    BigDecimal score = new BigDecimal("50.00");

    for (int i = 0; i < 100; i++) {
      score = calculator.calculate(
          score,
          new BigDecimal("90.00"),
          ActivityType.VIEW_DETAIL
      ).newScore();
    }

    assertTrue(score.compareTo(new BigDecimal("84.00")) >= 0);
    assertTrue(score.compareTo(new BigDecimal("86.00")) < 0);
  }

  @Test
  void rejectsScoresOutsideZeroToOneHundred() {
    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculate(-1, 50, ActivityType.LIKE)
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculate(50, 101, ActivityType.LIKE)
    );
  }
}

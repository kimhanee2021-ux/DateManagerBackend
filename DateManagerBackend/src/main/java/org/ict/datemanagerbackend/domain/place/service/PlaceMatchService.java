package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.ict.datemanagerbackend.domain.user.entity.UserStyle;
import org.springframework.stereotype.Service;

// 유저 성향값과 장소 성향값을 비교해 0~100 매칭 점수를 낸다. 단순히 5축 차이를 평균 내면 안 되는 이유는
// 2026-08-14 대화에서 나온 문제 때문 - 장소가 중립(50)으로 둔 축에서 유저가 우연히 50이면 "완벽 일치"로
// 잘못 잡힌다. 그래서 축별로 |장소값-50|을 가중치로 써서, 장소가 실제로 강조하는 축(50에서 많이 벗어난
// 축)일수록 그 축의 유사도가 최종 점수에 크게 반영되고, 장소가 중립인 축은 유저 값이 뭐든 거의
// 영향을 주지 않게 한다.
@Service
@RequiredArgsConstructor
public class PlaceMatchService {

  private final PlaceStyleRepository placeStyleRepository;

  private record Scores(int energy, int immersion, int vibe, int aesthetic, int depth) {
    static final Scores NEUTRAL = new Scores(50, 50, 50, 50, 50);
  }

  // 유저의 성향값과 장소의 성향값이 축별로 얼마나 가까운지를, 장소가 그 축을 얼마나 뚜렷하게
  // 강조하는지(50에서 벗어난 정도)로 가중평균한 매칭 점수(0~100)를 반환한다.
  public int calculateMatchScore(UserStyle userStyle, Place place) {
    Scores userScores = resolveUserScores(userStyle);
    Scores placeScores = resolvePlaceScores(place);

    double weightedSum = 0;
    double weightTotal = 0;

    weightedSum += similarity(userScores.energy(), placeScores.energy()) * weight(placeScores.energy());
    weightTotal += weight(placeScores.energy());

    weightedSum += similarity(userScores.immersion(), placeScores.immersion()) * weight(placeScores.immersion());
    weightTotal += weight(placeScores.immersion());

    weightedSum += similarity(userScores.vibe(), placeScores.vibe()) * weight(placeScores.vibe());
    weightTotal += weight(placeScores.vibe());

    weightedSum += similarity(userScores.aesthetic(), placeScores.aesthetic()) * weight(placeScores.aesthetic());
    weightTotal += weight(placeScores.aesthetic());

    weightedSum += similarity(userScores.depth(), placeScores.depth()) * weight(placeScores.depth());
    weightTotal += weight(placeScores.depth());

    // 장소가 5축 전부 중립(50)이면 가중치 합도 0 - 유저가 누구든 판단할 근거가 없다는 뜻이라 중간값을 준다.
    if (weightTotal == 0) {
      return 50;
    }
    return (int) Math.round(weightedSum / weightTotal);
  }

  private double similarity(int userValue, int placeValue) {
    return 100 - Math.abs(userValue - placeValue);
  }

  private double weight(int placeValue) {
    return Math.abs(placeValue - 50);
  }

  // 유저가 아직 성향검사를 안 했으면(온보딩 전, 필드가 null) 그 축은 중립값(50)으로 취급한다.
  private Scores resolveUserScores(UserStyle userStyle) {
    if (userStyle == null) {
      return Scores.NEUTRAL;
    }
    return new Scores(
        orNeutral(userStyle.getInitEnergy()),
        orNeutral(userStyle.getInitImmersion()),
        orNeutral(userStyle.getInitVibe()),
        orNeutral(userStyle.getInitAesthetic()),
        orNeutral(userStyle.getInitDepth())
    );
  }

  // 장소는 place_category(세부분류 공식)가 연결돼 있으면 그걸 우선 쓰고, 없으면 place_styles의
  // 중립값을 쓴다 - PlaceCategory 엔티티의 설계 의도(2026-08-14) 그대로.
  private Scores resolvePlaceScores(Place place) {
    PlaceCategory category = place.getPlaceCategory();
    if (category != null) {
      return new Scores(
          category.getScoreEnergy(),
          category.getScoreImmersion(),
          category.getScoreVibe(),
          category.getScoreAesthetic(),
          category.getScoreDepth()
      );
    }

    return placeStyleRepository.findByPlace_Id(place.getId())
        .map(this::toScores)
        .orElse(Scores.NEUTRAL);
  }

  private Scores toScores(PlaceStyle style) {
    return new Scores(
        style.getScoreEnergy(),
        style.getScoreImmersion(),
        style.getScoreVibe(),
        style.getScoreAesthetic(),
        style.getScoreDepth()
    );
  }

  private int orNeutral(Integer value) {
    return value != null ? value : 50;
  }
}

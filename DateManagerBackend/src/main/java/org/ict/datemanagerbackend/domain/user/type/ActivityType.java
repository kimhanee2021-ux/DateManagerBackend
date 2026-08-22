package org.ict.datemanagerbackend.domain.user.type;

import java.math.BigDecimal;

// 행동별 학습률의 단일 기준. DB에는 Enum 이름(VIEW_DETAIL 등)을 문자열로 저장하고,
// description은 화면/로그 설명에만 사용한다.
public enum ActivityType {

  VIEW_DETAIL("상세페이지 열람/스크롤", "0.02"),
  LIKE("좋아요/찜", "0.05"),
  ADD_TO_COURSE("코스에 담기/AI챗 명시적 언급", "0.10");

  private final String description;
  private final BigDecimal alpha;

  ActivityType(String description, String alpha) {
    this.description = description;
    this.alpha = new BigDecimal(alpha);
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAlpha() {
    return alpha;
  }
}

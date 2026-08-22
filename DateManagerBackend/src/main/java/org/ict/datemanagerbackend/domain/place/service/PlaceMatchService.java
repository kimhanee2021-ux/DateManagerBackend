package org.ict.datemanagerbackend.domain.place.service;

import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.user.entity.UserStyle;

// 유저 성향값과 장소 성향값을 비교해 0~100 매칭 점수를 내는 서비스의 인터페이스. admin 도메인의
// interface+impl 패턴과 통일하기 위해 분리함(2026-08-19). 계산 방식 설명은 PlaceMatchServiceImpl 참고.
public interface PlaceMatchService {
  // <<유저-장소 성향 매칭 점수(0~100) 계산>>
  int calculateMatchScore(UserStyle userStyle, Place place);
}

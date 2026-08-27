package org.ict.datemanagerbackend.domain.place.service;

import org.ict.datemanagerbackend.domain.place.entity.Place;

// 카테고리 매칭(SbizCategoryMatchService 등)이 특정 소스에서 장소를 못 찾았을 때, 카카오 로컬
// 검색으로 한 번 더 교차검증해서 "폐업 추정"을 판단하는 서비스. 원래 SbizCategoryMatchServiceImpl
// 안에 같이 있었는데, "카테고리 매칭"과 "폐업 추정"은 서로 다른 책임이라(전자를 테스트하려고 후자의
// 카카오 호출까지 매번 같이 도는 게 불필요하게 무거웠음) 분리함(2026-08-27).
public interface PlaceClosureCheckService {
  // <<이 장소를 카카오에서도 이름 일치로 못 찾으면 closureSuspected=true로 저장하고 true 반환.
  // 카카오에서 찾았거나 호출 자체가 실패하면(오탐 방지) 아무것도 안 하고 false 반환>>
  boolean flagIfKakaoAlsoMisses(Place place);
}

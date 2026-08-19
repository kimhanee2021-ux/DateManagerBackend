package org.ict.datemanagerbackend.domain.place.service;

import org.ict.datemanagerbackend.domain.place.entity.Place;

import java.util.Optional;

// 같은 실제 장소가 TourAPI/KOPIS/네이버/카카오 등 서로 다른 소스에서 각각 동기화되면서 중복 저장되는
// 걸 막기 위한 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과 통일하기 위해 분리함
// (2026-08-19). 실제 로직/설계 배경은 PlaceDedupServiceImpl 참고.
public interface PlaceDedupService {
  // <<이름+좌표가 비슷한 기존 장소(다른 소스에서 이미 저장한 것)를 찾아 반환. 없으면 신규 저장 대상>>
  Optional<Place> findDuplicate(String name, Double latitude, Double longitude);
}

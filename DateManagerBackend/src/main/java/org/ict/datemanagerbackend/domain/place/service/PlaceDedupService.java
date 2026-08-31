package org.ict.datemanagerbackend.domain.place.service;

import org.ict.datemanagerbackend.domain.place.entity.Place;

import java.util.Map;
import java.util.Optional;

// 같은 실제 장소가 TourAPI/KOPIS/네이버/카카오 등 서로 다른 소스에서 각각 동기화되면서 중복 저장되는
// 걸 막기 위한 서비스의 인터페이스. admin 도메인의 interface+impl 패턴과 통일하기 위해 분리함
// (2026-08-19). 실제 로직/설계 배경은 PlaceDedupServiceImpl 참고.
public interface PlaceDedupService {
  // <<이름+좌표가 비슷한 기존 장소(다른 소스에서 이미 저장한 것)를 찾아 반환. 없으면 신규 저장 대상>>
  Optional<Place> findDuplicate(String name, Double latitude, Double longitude);
  // <<반경 확대(2026-08-20) 이전에 이미 저장돼 있던, 이름이 완전히 같고 가까운 거리의 중복 장소를
  // 한 번에 정리 - 그룹마다 id가 가장 작은(가장 먼저 저장된) 장소만 남기고 나머지는 삭제>>
  Map<String, Integer> mergeDuplicatePlaces();
  // <<TourAPI 숙박(사진 보유) 항목을 순회하며, 이름이 완전히 같진 않아도(표기 차이) 반경 내에서 포함
  // 관계로 느슨하게 일치하고 사진이 없는 CSV 숙박(LODGING_STD) 항목을 찾아 사진만 채워준다(2026-08-27).
  // findDuplicate의 엄격한 이름 완전일치 조건 때문에 놓친 케이스를 보강하는 일회성 배치 - 삭제/덮어쓰기
  // 없이 사진 없는 곳에만 채워넣는다>>
  Map<String, Integer> backfillLodgingImagesFromTourApi();
}

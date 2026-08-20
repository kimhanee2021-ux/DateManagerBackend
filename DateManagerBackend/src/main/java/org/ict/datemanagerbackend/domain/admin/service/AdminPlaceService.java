package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.place.dto.PlaceDumpDto;

import java.util.List;
import java.util.Map;

public interface AdminPlaceService {
  // <<장소 동기화 수동 트리거 - source가 유효하지 않으면 IllegalArgumentException>>
  void syncSource(String source);
  // <<블랙리스트 필터 도입 이전에 저장된 프랜차이즈/체인점 정리>>
  Map<String, Integer> cleanupBlacklistedPlaces();
  // <<dedup 반경 확대 이전에 저장된, 이름 같고 가까운 거리의 중복 장소 정리>>
  Map<String, Integer> mergeDuplicatePlaces();
  // <<대분류별 장소 개수 + 전체 개수>>
  Map<String, Long> getSyncStatus();
  // <<전체 장소 백업(CSV 본문)>>
  String exportPlacesCsv();
  // <<장소 세부분류(성향점수 공식) 전체 백업(CSV 본문)>>
  String exportPlaceCategoriesCsv();
  // <<exportPlaceCategoriesCsv로 받은 CSV를 parentCategory+subCategory로 매칭해 점수 upsert. 생성/갱신/건너뜀 건수 반환>>
  Map<String, Integer> importPlaceCategoriesCsv(String csv);
  // <<팀원 공유용 place 전체 백업(성향점수/현실체킹/편의시설 포함, 카테고리 연결은 제외) - JSON>>
  List<PlaceDumpDto> exportFullDump();
  // <<exportFullDump로 받은 데이터를 이름+좌표 또는 외부소스 키로 매칭해 내 DB에 반영. 생성/갱신 건수 반환>>
  Map<String, Integer> importFullDump(List<PlaceDumpDto> dump);
  // <<TourAPI 출처 장소 좌표를 카카오와 대조해 재검증/교정 - 수십 분 이상 걸려서 백그라운드로 돌리고 바로 반환>>
  Map<String, String> verifyTourApiCoordinates();
}

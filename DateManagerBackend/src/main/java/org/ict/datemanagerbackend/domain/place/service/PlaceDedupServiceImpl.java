package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 같은 실제 장소가 TourAPI/KOPIS/네이버/카카오 등 서로 다른 소스에서 각각 동기화되면서 중복 저장되는
// 걸 막기 위한 서비스. 각 동기화 서비스는 새 장소를 만들기 직전에 이 서비스로 "이미 다른 소스가
// 저장해둔 같은 장소인지"를 확인한다. (external_source+external_id로는 같은 소스 내 중복만 막을 수
// 있고, 소스가 다르면 애초에 값이 달라서 걸러지지 않기 때문에 별도 로직이 필요함)
@Service
@RequiredArgsConstructor
public class PlaceDedupServiceImpl implements PlaceDedupService {

    // 위경도 대략 0.0025도 = 약 200~280m 반경(위도 기준 약 280m, 이 서비스가 다루는 위도대인 북위
    // 37~38도 부근에서 경도 기준 약 200m). 원래는 0.0005도(약 50m)였는데, "코트야드 바이 메리어트
    // 서울타임스퀘어" 같은 같은 상호가 TourAPI/카카오/숙박업 CSV마다 최대 약 190m씩 다른 좌표로
    // 찍혀 들어와서(건물 중심점 vs 출입구 등 소스마다 지오코딩 기준이 다른 듯) 50m로는 못 잡고
    // 3중복이 쌓이는 걸 확인함(2026-08-20). 반경을 넓혀도 안전한 이유는 아래 findDuplicate에서
    // "정규화한 이름이 완전히 같을 때만" 같은 장소로 인정하기 때문 - 이름까지 같은 두 장소가 우연히
    // 200m 안에 있을 확률은 낮아서, 이 조건과 묶으면 오탐(실제로 다른 두 매장을 합쳐버리는 것) 위험이
    // 크지 않다고 판단함. 반대로 이름이 다르면(예: "메리어트" vs "매리엇" 표기 차이) 아무리 가까워도
    // 안 합쳐지므로, 그런 표기 차이 중복은 이 개선으로는 못 잡는다 - 별도 판단이 필요한 영역.
    private static final double RADIUS_DEGREES = 0.0025;

    private final PlaceRepository placeRepository;
    private final PlaceStyleRepository placeStyleRepository;

    // 이름+좌표가 비슷한 기존 장소(다른 소스에서 이미 저장한 것)를 찾아서 반환한다. 없으면 신규 저장 대상.
    @Override
    public Optional<Place> findDuplicate(String name, Double latitude, Double longitude) {
        if (name == null || name.isBlank() || latitude == null || longitude == null) {
            return Optional.empty();
        }
        String normalized = normalize(name);
        List<Place> nearby = placeRepository.findNearby(
                latitude - RADIUS_DEGREES, latitude + RADIUS_DEGREES,
                longitude - RADIUS_DEGREES, longitude + RADIUS_DEGREES);

        return nearby.stream()
                .filter(p -> normalize(p.getName()).equals(normalized))
                .findFirst();
    }

    // RADIUS_DEGREES를 0.0005도(약 50m)에서 0.0025도(약 200~280m)로 넓히기 전에 이미 저장돼 있던
    // 중복(예: "코트야드 바이 메리어트 서울타임스퀘어"가 KAKAO/TOURAPI/LODGING_STD 세 소스로 각각
    // 저장된 것)을 한 번에 정리하는 관리자 전용 일회성 배치(2026-08-20). findDuplicate와 똑같이
    // "정규화한 이름이 완전히 같고 반경 안에 있을 때만" 같은 장소로 보고, 그룹에서 id가 가장 작은
    // (가장 먼저 저장된) 장소만 남긴다 - 어느 소스든 먼저 들어온 걸 기준으로 삼으면 충분하고, 굳이
    // 소스별 우선순위를 따로 정할 실익이 없어서다.
    @Override
    public Map<String, Integer> mergeDuplicatePlaces() {
        int merged = 0;
        int skipped = 0;
        Set<Long> handled = new HashSet<>();

        for (Place place : placeRepository.findAll()) {
            if (handled.contains(place.getId())
                    || place.getLatitude() == null || place.getLongitude() == null
                    || place.getName() == null || place.getName().isBlank()) {
                continue;
            }

            String normalized = normalize(place.getName());
            List<Place> nearby = placeRepository.findNearby(
                    place.getLatitude() - RADIUS_DEGREES, place.getLatitude() + RADIUS_DEGREES,
                    place.getLongitude() - RADIUS_DEGREES, place.getLongitude() + RADIUS_DEGREES);

            List<Place> group = new ArrayList<>();
            group.add(place);
            for (Place candidate : nearby) {
                if (!candidate.getId().equals(place.getId()) && normalize(candidate.getName()).equals(normalized)) {
                    group.add(candidate);
                }
            }
            if (group.size() < 2) {
                continue;
            }

            Place keeper = group.stream().min(Comparator.comparing(Place::getId)).orElseThrow();
            for (Place duplicate : group) {
                handled.add(duplicate.getId());
                if (duplicate.getId().equals(keeper.getId())) {
                    continue;
                }
                try {
                    placeStyleRepository.findByPlace_Id(duplicate.getId()).ifPresent(placeStyleRepository::delete);
                    placeRepository.delete(duplicate);
                    placeRepository.flush();
                    merged++;
                } catch (DataIntegrityViolationException e) {
                    skipped++;
                }
            }
        }

        return Map.of("merged", merged, "skipped", skipped);
    }

    // 공백/괄호를 지우고 소문자로 바꿔서 비교 - "스타벅스 강남점"과 "스타벅스강남점" 같은 표기 차이를 흡수한다.
    private String normalize(String name) {
        return name.replaceAll("\\s+", "")
                .replaceAll("[()（）\\[\\]]", "")
                .toLowerCase();
    }
}

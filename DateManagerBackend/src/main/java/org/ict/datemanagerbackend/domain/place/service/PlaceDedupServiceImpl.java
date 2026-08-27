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

    // findDuplicate/mergeDuplicatePlaces는 정규화한 이름이 "완전히 같아야만" 합치는데, 실제로는
    // "용평제4차콘도미니엄(그린피아)"(CSV) vs TourAPI 쪽 표기 차이처럼 완전일치가 안 되는 중복이 많다
    // (2026-08-27 실측 - TourAPI 숙박 2,041건 중 514건만 완전일치로 매칭됨). 여기서는 사진을 이미
    // 갖고 있는 TourAPI 숙박 항목을 기준으로, 반경 내에서 이름이 "포함 관계"(느슨한 부분 일치, SBIZ
    // 매칭과 동일 방식)이고 사진이 없는 CSV 숙박 항목에 사진만 채워준다. 삭제나 다른 필드 덮어쓰기는
    // 하지 않는다 - 정규화 이름이 다른 별개 장소를 잘못 합치는 위험을 피하려고, 매칭 후보가 여러 개면
    // 가장 가까운 거리 하나에만 채운다.
    @Override
    public Map<String, Integer> backfillLodgingImagesFromTourApi() {
        int filled = 0;
        int skipped = 0;

        List<Place> tourApiLodging = placeRepository.findAll().stream()
                .filter(p -> "TOURAPI".equals(p.getExternalSource()) && "숙박".equals(p.getCategory())
                        && p.getImageUrl() != null && !p.getImageUrl().isBlank()
                        && p.getLatitude() != null && p.getLongitude() != null
                        && p.getName() != null && !p.getName().isBlank())
                .toList();

        for (Place source : tourApiLodging) {
            String normalizedSource = normalize(source.getName());
            List<Place> nearby = placeRepository.findNearby(
                    source.getLatitude() - RADIUS_DEGREES, source.getLatitude() + RADIUS_DEGREES,
                    source.getLongitude() - RADIUS_DEGREES, source.getLongitude() + RADIUS_DEGREES);

            Place best = null;
            double bestDistSq = Double.MAX_VALUE;
            for (Place candidate : nearby) {
                if (!"LODGING_STD".equals(candidate.getExternalSource())) continue;
                if (candidate.getImageUrl() != null && !candidate.getImageUrl().isBlank()) continue;
                if (candidate.getName() == null || candidate.getName().isBlank()) continue;

                String normalizedCandidate = normalize(candidate.getName());
                if (normalizedCandidate.isEmpty()) continue;
                if (!normalizedSource.contains(normalizedCandidate) && !normalizedCandidate.contains(normalizedSource)) {
                    continue;
                }

                double dLat = candidate.getLatitude() - source.getLatitude();
                double dLng = candidate.getLongitude() - source.getLongitude();
                double distSq = dLat * dLat + dLng * dLng;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    best = candidate;
                }
            }

            if (best != null) {
                best.setImageUrl(source.getImageUrl());
                placeRepository.save(best);
                filled++;
            } else {
                skipped++;
            }
        }

        return Map.of("filled", filled, "skipped", skipped);
    }

    // 공백/괄호를 지우고 소문자로 바꿔서 비교 - "스타벅스 강남점"과 "스타벅스강남점" 같은 표기 차이를 흡수한다.
    private String normalize(String name) {
        return name.replaceAll("\\s+", "")
                .replaceAll("[()（）\\[\\]]", "")
                .toLowerCase();
    }
}

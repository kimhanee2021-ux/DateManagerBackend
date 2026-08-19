package org.ict.datemanagerbackend.domain.place.service;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// 같은 실제 장소가 TourAPI/KOPIS/네이버/카카오 등 서로 다른 소스에서 각각 동기화되면서 중복 저장되는
// 걸 막기 위한 서비스. 각 동기화 서비스는 새 장소를 만들기 직전에 이 서비스로 "이미 다른 소스가
// 저장해둔 같은 장소인지"를 확인한다. (external_source+external_id로는 같은 소스 내 중복만 막을 수
// 있고, 소스가 다르면 애초에 값이 달라서 걸러지지 않기 때문에 별도 로직이 필요함)
@Service
@RequiredArgsConstructor
public class PlaceDedupServiceImpl implements PlaceDedupService {

    // 위경도 대략 0.0005도 = 약 50m 반경. 같은 상호명이 그 안에 있으면 같은 장소로 간주한다.
    private static final double RADIUS_DEGREES = 0.0005;

    private final PlaceRepository placeRepository;

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

    // 공백/괄호를 지우고 소문자로 바꿔서 비교 - "스타벅스 강남점"과 "스타벅스강남점" 같은 표기 차이를 흡수한다.
    private String normalize(String name) {
        return name.replaceAll("\\s+", "")
                .replaceAll("[()（）\\[\\]]", "")
                .toLowerCase();
    }
}

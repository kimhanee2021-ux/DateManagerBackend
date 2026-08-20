package org.ict.datemanagerbackend.domain.course.service;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponseDto;
import org.ict.datemanagerbackend.domain.course.dto.CourseMatchSuggestionDto;
import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.course.entity.CourseItem;
import org.ict.datemanagerbackend.domain.course.repository.CourseGroupRepository;
import org.ict.datemanagerbackend.domain.course.repository.CourseItemRepository;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 아이디어안("데이트 코스 빌더 - Course Sync Builder")의 [사전 계획 모드] 중 CRUD + Step 2(자동 연결)까지만
 * 구현한다 - Step 3(실시간 정렬)과 [현장 라이브 모드](무드 스위처/GPS 리라우팅)는 2026-08-20 사용자
 * 결정으로 이번 스코프에서 제외(CourseLiveStatus/CourseReRouting/CourseLiveLog 관련 로직 없음).
 *
 * <p>couple은 일부러 항상 null로 둔다(솔로 모드) - CourseGroup 주석대로 커플 연결은 선택 사항이고,
 * 지금 목표는 "place 데이터가 코스빌더에 실제로 연결되는지" 검증이라 커플 매칭 로직까지 얹으면
 * 스코프가 커진다. 필요해지면 나중에 채울 것.
 */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

  // 아이디어안 "이동 시간 20분 이내" - 실시간 경로 API가 없어서 도보 기준 근사치로 거리를 쓴다.
  // 성인 평균 도보 속도 약 4~4.5km/h -> 분당 약 75m, 20분이면 약 1,500m.
  private static final double MAX_WALK_METERS = 1500.0;
  private static final double METERS_PER_MINUTE = 75.0;
  private static final int NEARBY_POOL_SIZE = 60;
  private static final int SUGGESTION_LIMIT = 5;
  private static final double EARTH_RADIUS_METERS = 6371000.0;

  private final CourseGroupRepository courseGroupRepository;
  private final CourseItemRepository courseItemRepository;
  private final PlaceRepository placeRepository;
  private final PlaceStyleRepository placeStyleRepository;

  @Override
  @Transactional
  public CourseGroupResponseDto createGroup(User user, String title) {
    CourseGroup group = CourseGroup.builder()
        .user(user)
        .couple(null)
        .title(title)
        .build();
    courseGroupRepository.save(group);
    return CourseGroupResponseDto.from(group, List.of());
  }

  @Override
  @Transactional(readOnly = true)
  public List<CourseGroupResponseDto> listGroups(User user) {
    return courseGroupRepository.findByUser_IdOrderByIdDesc(user.getId()).stream()
        .map(group -> CourseGroupResponseDto.from(
            group, courseItemRepository.findByCourseGroup_IdOrderBySequenceAsc(group.getId())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public CourseGroupResponseDto getGroup(User user, Long courseGroupId) {
    CourseGroup group = getOwnedGroup(user, courseGroupId);
    return CourseGroupResponseDto.from(
        group, courseItemRepository.findByCourseGroup_IdOrderBySequenceAsc(group.getId()));
  }

  @Override
  @Transactional
  public CourseGroupResponseDto addItem(User user, Long courseGroupId, Long placeId) {
    CourseGroup group = getOwnedGroup(user, courseGroupId);
    Place place = placeRepository.findById(placeId)
        .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다: " + placeId));

    int nextSequence = courseItemRepository.findFirstByCourseGroup_IdOrderBySequenceDesc(courseGroupId)
        .map(item -> item.getSequence() + 1)
        .orElse(1); // 첫 장소면 1번(=아이디어안의 "메인 거점")

    CourseItem item = CourseItem.builder()
        .courseGroup(group)
        .place(place)
        .sequence(nextSequence)
        .build();
    courseItemRepository.save(item);

    return CourseGroupResponseDto.from(
        group, courseItemRepository.findByCourseGroup_IdOrderBySequenceAsc(courseGroupId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<CourseMatchSuggestionDto> autoMatch(User user, Long courseGroupId) {
    CourseGroup group = getOwnedGroup(user, courseGroupId);
    List<CourseItem> items = courseItemRepository.findByCourseGroup_IdOrderBySequenceAsc(group.getId());
    if (items.isEmpty()) {
      throw new IllegalArgumentException("코스에 거점 장소를 먼저 담아야 자동 연결을 쓸 수 있습니다");
    }

    // 가장 최근에 담은 장소를 기준점으로 삼는다 - 코스를 이어 짜는 상황에서는 "방금 그 장소 다음엔
    // 어디"가 자연스러운 흐름이고, 장소가 하나뿐이면 자연히 메인 거점 자체가 기준이 된다.
    Place anchor = items.get(items.size() - 1).getPlace();
    if (anchor.getLatitude() == null || anchor.getLongitude() == null) {
      return List.of(); // 좌표 없는 거점은 거리 계산이 불가능해서 추천을 못 만든다
    }

    Set<Long> alreadyInCourse = new HashSet<>();
    for (CourseItem item : items) {
      alreadyInCourse.add(item.getPlace().getId());
    }

    Integer anchorEnergy = resolveEnergy(anchor);
    // "성향 보완" 기준(2026-08-20 1차 구현): 5축 점수를 다 쓰기보다, 아이디어안 예시("도파민형 활동
    // 후 릴랙스형 카페")가 정확히 에너지 축 이야기라서 에너지 하나만으로 시작한다 - 거점과 반대
    // 방향(50을 기준으로 대칭)에 가까운 에너지 점수를 가진 곳일수록 "보완"에 가깝다고 봄.
    int idealComplementEnergy = 100 - anchorEnergy;

    List<Place> pool = placeRepository.findNearestPlaces(anchor.getLatitude(), anchor.getLongitude(), NEARBY_POOL_SIZE);

    List<SuggestionWithGap> candidates = new ArrayList<>();
    for (Place candidate : pool) {
      if (alreadyInCourse.contains(candidate.getId())) continue;
      if (candidate.getLatitude() == null || candidate.getLongitude() == null) continue;

      double distanceMeters = haversineMeters(
          anchor.getLatitude(), anchor.getLongitude(), candidate.getLatitude(), candidate.getLongitude());
      if (distanceMeters > MAX_WALK_METERS) continue;

      Integer candidateEnergy = resolveEnergy(candidate);
      int complementGap = Math.abs(candidateEnergy - idealComplementEnergy);
      PlaceCategory category = candidate.getPlaceCategory();

      candidates.add(new SuggestionWithGap(
          new CourseMatchSuggestionDto(
              candidate.getId(), candidate.getName(), candidate.getCategory(),
              category != null ? category.getSubCategory() : null,
              candidate.getAddress(), candidate.getLatitude(), candidate.getLongitude(),
              candidateEnergy, Math.round(distanceMeters * 10) / 10.0,
              (int) Math.ceil(distanceMeters / METERS_PER_MINUTE)
          ),
          complementGap
      ));
    }

    return candidates.stream()
        .sorted(Comparator.comparingInt(SuggestionWithGap::complementGap))
        .limit(SUGGESTION_LIMIT)
        .map(SuggestionWithGap::dto)
        .toList();
  }

  private record SuggestionWithGap(CourseMatchSuggestionDto dto, int complementGap) {
  }

  // CurationPlaceDto와 같은 우선순위(place_category 우선, 없으면 place_styles, 그마저 없으면 중립 50).
  private Integer resolveEnergy(Place place) {
    PlaceCategory category = place.getPlaceCategory();
    if (category != null) return category.getScoreEnergy();
    return placeStyleRepository.findByPlace_Id(place.getId())
        .map(PlaceStyle::getScoreEnergy)
        .orElse(50);
  }

  private CourseGroup getOwnedGroup(User user, Long courseGroupId) {
    CourseGroup group = courseGroupRepository.findById(courseGroupId)
        .orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다: " + courseGroupId));
    if (!group.getUser().getId().equals(user.getId())) {
      throw new IllegalArgumentException("본인이 만든 코스만 접근할 수 있습니다");
    }
    return group;
  }

  // PlaceRepository.findNearestPlaces와 같은 구면삼각법 공식(반경만 km->m로 다름) - 정렬은 이미
  // 그 쿼리가 해주지만, 실제 거리(m) 값과 20분 컷오프 판정에 쓸 숫자가 필요해서 자바에서 한 번 더 계산한다.
  private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_METERS * c;
  }
}

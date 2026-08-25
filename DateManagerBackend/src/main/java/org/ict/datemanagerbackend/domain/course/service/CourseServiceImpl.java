package org.ict.datemanagerbackend.domain.course.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleMember;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponse;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponseWithCoords;
import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.course.entity.CourseItem;
import org.ict.datemanagerbackend.domain.course.repository.CourseGroupRepository;
import org.ict.datemanagerbackend.domain.course.repository.CourseItemRepository;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

  private final CourseGroupRepository courseGroupRepository;
  private final PlaceRepository placeRepository;
  private final CourseItemRepository courseItemRepository;
  private final CoupleRepository coupleRepository;
  private final CoupleMemberRepository coupleMemberRepository;
  private final UserRepository userRepository;

  @Transactional
  @Override
  public CourseGroupResponse createCourseGroup(Long userId, CourseGroupCreateRequest request) {

    //코스 그룹 생성 로직
    User user = userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("User Not Found"));
    // coupleId는 요청 body가 아니라 내 활성 커플 멤버십에서 직접 찾는다(2026-08-25) - 클라이언트가
    // 보내는 값을 그대로 믿지 않는 기존 보안 원칙(userId와 동일)과, 커플로 연결돼 있으면 자동으로
    // 코스가 공유돼야 한다는 요구사항을 같이 만족시킨다.
    Couple couple = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(userId)
        .map(CoupleMember::getCouple)
        .orElse(null);
    CourseGroup courseGroup = CourseGroup.builder()
        .title(request.title())
        .user(user)
        .couple(couple)
        .build();
    //그룹 생성과 동시에 만들어진 레코드에서 id 저장
    CourseGroup finalCourseGroup = courseGroupRepository.save(courseGroup);
    //코스 아이템 생성 로직
    List<CourseItem> courseItems = IntStream.range(0, request.placeIds().size()).mapToObj(i -> {
      Long placeId = request.placeIds().get(i);
      Place place = placeRepository.getReferenceById(placeId);

      return CourseItem.builder().courseGroup(finalCourseGroup).place(place).sequence(i + 1).build();
    }).toList();


    courseItemRepository.saveAll(courseItems);


    return CourseGroupResponse.from(finalCourseGroup);
  }

  @Transactional
  @Override
  public List<CourseGroupResponseWithCoords> listMyCourseGroups(Long userId) {
    if (!userRepository.existsById(userId)) {
      throw new NoSuchElementException("User Not Found");
    }
    // 내가 만든 코스뿐 아니라, 지금 연결된 커플이 있으면 파트너가 만든 코스도 같이 보여준다
    // (2026-08-25) - CourseGroup에 couple 필드가 있었는데도 조회는 user 기준으로만 돼있어서
    // 커플끼리 서로 만든 코스를 못 보던 문제.
    Long coupleId = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(userId)
        .map(member -> member.getCouple().getId())
        .orElse(null);
    List<CourseGroup> groups = courseGroupRepository.findByUser_IdOrCouple_Id(userId, coupleId);

    if (groups.isEmpty()) {
      return List.of();
    }
    List<Long> groupIds = groups.stream().map(CourseGroup::getId).toList();

    List<CourseItem> allItems = courseItemRepository.findByCourseGroupIdInWithPlace(groupIds);

    Map<Long, List<CourseItem>> itemsByGroupId = allItems.stream()
        .collect(Collectors.groupingBy(item -> item.getCourseGroup().getId()));

    return groups.stream()
        .map(group -> CourseGroupResponseWithCoords.of(
            group,
            itemsByGroupId.getOrDefault(group.getId(), List.of())
        )).toList();

  }

  // 코스 안에서 장소 순서를 한 칸 위/아래로 바꾼다. 프론트가 place.id를 식별자로 쓰고 있어서
  // placeId 기준으로 대상을 찾는다(같은 코스 안에서는 place가 중복 담기지 않는다는 전제).
  @Transactional
  @Override
  public void moveCourseItem(Long userId, Long groupId, Long placeId, String direction) {
    CourseGroup group = courseGroupRepository.findById(groupId)
        .orElseThrow(() -> new NoSuchElementException("Course Group Not Found"));

    if (!group.getUser().getId().equals(userId)) {
      throw new IllegalStateException("본인이 만든 코스만 수정할 수 있습니다");
    }

    List<CourseItem> items = courseItemRepository.findByCourseGroup_IdOrderBySequenceAsc(groupId);

    int currentIndex = -1;
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i).getPlace().getId().equals(placeId)) {
        currentIndex = i;
        break;
      }
    }
    if (currentIndex == -1) {
      throw new NoSuchElementException("Course Item Not Found");
    }

    int targetIndex = "up".equals(direction) ? currentIndex - 1 : currentIndex + 1;
    if (targetIndex < 0 || targetIndex >= items.size()) {
      return; // 이미 맨 위/맨 아래라 더 옮길 곳이 없음 - 아무것도 안 하고 조용히 끝낸다.
    }

    CourseItem current = items.get(currentIndex);
    CourseItem target = items.get(targetIndex);

    // course_items에 (course_group_id, sequence) 유니크 제약이 걸려있어서, current를
    // target의 sequence로 먼저 바꿔버리면 target이 아직 그 값을 갖고 있는 순간
    // 유니크 제약을 위반해서 500 에러가 났다("순서 변경 중 오류가 발생했습니다",
    // 2026-08-25 발견). -1(다른 행과 절대 안 겹치는 값)을 거쳐서 3단계로 바꾸고,
    // 중간에 flush해서 매 단계가 실제로 겹치지 않게 한다.
    int currentSequence = current.getSequence();
    int targetSequence = target.getSequence();

    current.setSequence(-1);
    courseItemRepository.flush();

    target.setSequence(currentSequence);
    courseItemRepository.flush();

    current.setSequence(targetSequence);
  }

  // 등록된 코스 삭제(2026-08-25 추가) - course_items에 FK가 걸려있어서 그룹보다 먼저
  // 소속 아이템부터 지워야 한다.
  @Transactional
  @Override
  public void deleteCourseGroup(Long userId, Long groupId) {
    CourseGroup group = courseGroupRepository.findById(groupId)
        .orElseThrow(() -> new NoSuchElementException("Course Group Not Found"));

    if (!group.getUser().getId().equals(userId)) {
      throw new IllegalStateException("본인이 만든 코스만 삭제할 수 있습니다");
    }

    courseItemRepository.deleteByCourseGroup_Id(groupId);
    courseGroupRepository.delete(group);
  }

}

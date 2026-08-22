package org.ict.datemanagerbackend.domain.course.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponse;
import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.course.repository.CourseGroupRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

  private final CourseGroupRepository courseGroupRepository;
  private final CoupleRepository coupleRepository;
  private final UserRepository userRepository;

  @Transactional
  @Override
  public CourseGroupResponse createCourseGroup(Long userId, CourseGroupCreateRequest request) {
    User user = userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("User Not Found"));
    Couple couple = null;
    if (request.coupleId() != null) {
      couple = coupleRepository.findById(request.coupleId()).orElseThrow(() -> new NoSuchElementException("Couple Not Found"));
    }
    CourseGroup courseGroup = CourseGroup.builder()
        .title(request.title())
        .user(user)
        .couple(couple)
        .build();
    courseGroupRepository.save(courseGroup);
    return CourseGroupResponse.from(courseGroup);
  }

  @Override
  public List<CourseGroupResponse> listMyCourseGroups(Long userId) {
    return courseGroupRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
        .map(CourseGroupResponse::from)
        .toList();
  }
}

package org.ict.datemanagerbackend.domain.course.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseItemRequestDto;
import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.course.repository.CourseGroupRepository;
import org.ict.datemanagerbackend.domain.course.repository.CourseItemRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

  private final CourseGroupRepository courseGroupRepository;
  private final CourseItemRepository courseItemRepository;
  private final CoupleRepository coupleRepository;
  private final UserRepository userRepository;

  @Transactional
  @Override
  public void CreateCourseGroup(CourseGroupCreateRequest courseGroupCreateRequest) {
    //존재하는 유저인지 체크
    User user = userRepository.findById(courseGroupCreateRequest.getUserId()).orElseThrow(() -> new NoSuchElementException("User Not Found"));
    //커플 여부 확인
    Couple couple = null;
    if (courseGroupCreateRequest.getCoupleId() != null) {
      couple = coupleRepository.findById(courseGroupCreateRequest.getCoupleId()).orElseThrow(() -> new NoSuchElementException("Couple Not Found"));
    }
    CourseGroup courseGroup = courseGroupCreateRequest.toEntity(user, couple);
    courseGroupRepository.save(courseGroup);


  }

  @Override
  public void addCourseItem(CourseItemRequestDto courseItemRequestDto) {

  }
}


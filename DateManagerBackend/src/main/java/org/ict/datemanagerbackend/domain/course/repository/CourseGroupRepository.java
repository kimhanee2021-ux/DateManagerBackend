package org.ict.datemanagerbackend.domain.course.repository;

import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


public interface CourseGroupRepository extends JpaRepository<CourseGroup,Long> {

  // 빌더 화면 "내 코스" 목록용 - 방금 만든 코스그룹이 최상단에 오도록 최신순으로 내려준다.
  List<CourseGroup> findByUser_IdOrderByCreatedAtDesc(Long userId);

  List<CourseGroup> findCourseGroupsByUser(User user);

  // 내가 만든 코스 + 내 커플이 공유한 코스를 한 번에 조회(2026-08-25 추가) - coupleId가 null이면
  // "couple_id = null"은 항상 거짓이라 커플 조건은 자동으로 무시되고 user 조건만 남는다.
  List<CourseGroup> findByUser_IdOrCouple_Id(Long userId, Long coupleId);

}



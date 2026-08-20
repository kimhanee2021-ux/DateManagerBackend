package org.ict.datemanagerbackend.domain.course.repository;

import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseGroupRepository extends JpaRepository<CourseGroup,Long> {

  // 마이 코스빌더 목록용 - 내가 만든 코스 그룹을 최신순으로.
  List<CourseGroup> findByUser_IdOrderByIdDesc(Long userId);

}

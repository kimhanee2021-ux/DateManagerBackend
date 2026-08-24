package org.ict.datemanagerbackend.domain.course.repository;

import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.course.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseItemRepository extends JpaRepository<CourseItem,Long> {
  List<CourseItem> findAllByCourseGroup(CourseGroup courseGroup);

  // 순서 변경(위/아래) 시 현재 순번대로 인접 아이템을 찾기 위한 조회
  List<CourseItem> findByCourseGroup_IdOrderBySequenceAsc(Long courseGroupId);

  @Query("SELECT ci FROM CourseItem ci " +
      "JOIN FETCH ci.place " +
      "WHERE ci.courseGroup.id IN :groupIds " +
      "ORDER BY ci.courseGroup.id ASC, ci.sequence ASC")
  List<CourseItem> findByCourseGroupIdInWithPlace(@Param("groupIds") List<Long> groupIds);
}

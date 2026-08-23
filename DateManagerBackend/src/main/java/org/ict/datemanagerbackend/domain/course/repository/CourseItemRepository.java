package org.ict.datemanagerbackend.domain.course.repository;

import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.course.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseItemRepository extends JpaRepository<CourseItem,Long> {
  List<CourseItem> findAllByCourseGroup(CourseGroup courseGroup);

  @Query("SELECT ci FROM CourseItem ci " +
      "JOIN FETCH ci.place " +
      "WHERE ci.courseGroup.id IN :groupIds " +
      "ORDER BY ci.courseGroup.id ASC, ci.sequence ASC")
  List<CourseItem> findByCourseGroupIdInWithPlace(@Param("groupIds") List<Long> groupIds);
}

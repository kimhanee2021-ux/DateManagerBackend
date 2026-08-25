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

  // 코스 그룹 삭제 시 먼저 지워야 하는 소속 아이템들 - course_items.course_group_id에
  // ON DELETE CASCADE가 없어서, 그룹만 지우면 FK 제약 위반이 난다.
  void deleteByCourseGroup_Id(Long courseGroupId);

  @Query("SELECT ci FROM CourseItem ci " +
      "JOIN FETCH ci.place " +
      "WHERE ci.courseGroup.id IN :groupIds " +
      "ORDER BY ci.courseGroup.id ASC, ci.sequence ASC")
  List<CourseItem> findByCourseGroupIdInWithPlace(@Param("groupIds") List<Long> groupIds);
}

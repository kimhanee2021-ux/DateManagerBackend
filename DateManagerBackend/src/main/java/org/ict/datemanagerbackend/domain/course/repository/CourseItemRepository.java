package org.ict.datemanagerbackend.domain.course.repository;

import org.ict.datemanagerbackend.domain.course.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CourseItemRepository extends JpaRepository<CourseItem, Long> {

  // 코스 상세 조회 시 타임라인 순서(sequence) 그대로 보여주기 위해 정렬해서 가져온다.
  List<CourseItem> findByCourseGroup_IdOrderBySequenceAsc(Long courseGroupId);

  // 새 장소를 코스 맨 뒤에 추가할 때 "지금까지 몇 번째까지 채워져 있는지" 확인용.
  Optional<CourseItem> findFirstByCourseGroup_IdOrderBySequenceDesc(Long courseGroupId);

  int countByCourseGroup_Id(Long courseGroupId);

  // 코스 그룹을 통째로 지울 때(CourseServiceImpl.deleteGroup) 먼저 담긴 장소부터 다 지운다 -
  // course_items가 course_group_id를 FK로 물고 있어서, 부모(course_groups)를 먼저 지우면 FK 위반이 난다.
  // PlaceRepository.deleteByExternalSource와 같은 이유로 derived delete엔 @Transactional이 필요하다.
  @Transactional
  void deleteByCourseGroup_Id(Long courseGroupId);
}

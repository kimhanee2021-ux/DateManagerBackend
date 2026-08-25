package org.ict.datemanagerbackend.domain.course.service;

import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponse;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponseWithCoords;

import java.util.List;

public interface CourseService {

  CourseGroupResponse createCourseGroup(Long userId, CourseGroupCreateRequest request);

  List<CourseGroupResponseWithCoords> listMyCourseGroups(Long userId);

  void moveCourseItem(Long userId, Long groupId, Long placeId, String direction);

  void deleteCourseGroup(Long userId, Long groupId);

  // <<코스 안 장소들을 이동 거리가 최소가 되는 순서로 재배열(첫 장소는 고정, 나머지를 최근접 이웃으로)>>
  void optimizeOrder(Long userId, Long groupId);

}

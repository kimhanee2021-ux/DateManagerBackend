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

}

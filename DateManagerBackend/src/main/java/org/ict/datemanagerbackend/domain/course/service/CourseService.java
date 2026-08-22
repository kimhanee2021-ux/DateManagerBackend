package org.ict.datemanagerbackend.domain.course.service;

import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponse;

import java.util.List;

public interface CourseService {

  CourseGroupResponse createCourseGroup(Long userId, CourseGroupCreateRequest request);

  List<CourseGroupResponse> listMyCourseGroups(Long userId);

}

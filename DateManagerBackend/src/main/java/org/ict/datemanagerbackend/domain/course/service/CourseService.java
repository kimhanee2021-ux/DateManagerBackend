package org.ict.datemanagerbackend.domain.course.service;

import org.ict.datemanagerbackend.domain.course.dto.CourseGroupCreateRequest;
import org.ict.datemanagerbackend.domain.course.dto.CourseItemRequestDto;


public interface CourseService {

  void CreateCourseGroup(CourseGroupCreateRequest courseGroupCreateRequest);

  void addCourseItem(CourseItemRequestDto courseItemRequestDto);



}

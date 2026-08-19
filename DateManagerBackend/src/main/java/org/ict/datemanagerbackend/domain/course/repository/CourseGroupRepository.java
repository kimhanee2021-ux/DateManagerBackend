package org.ict.datemanagerbackend.domain.course.repository;

import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


public interface CourseGroupRepository extends JpaRepository<CourseGroup,Long> {


}

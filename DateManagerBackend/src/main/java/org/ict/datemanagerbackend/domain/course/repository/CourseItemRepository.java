package org.ict.datemanagerbackend.domain.course.repository;

import org.ict.datemanagerbackend.domain.course.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseItemRepository extends JpaRepository<CourseItem, Long> {

}

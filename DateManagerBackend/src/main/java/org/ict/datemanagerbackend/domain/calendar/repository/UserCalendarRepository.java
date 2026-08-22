package org.ict.datemanagerbackend.domain.calendar.repository;

import org.ict.datemanagerbackend.domain.calendar.entity.UserCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface UserCalendarRepository extends JpaRepository<UserCalendar, Long> {
  List<UserCalendar> findByUserIdAndTargetDateBetween(Long userId, LocalDate start, LocalDate end);
}

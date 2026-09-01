package org.ict.datemanagerbackend.domain.calendar.repository;

import org.ict.datemanagerbackend.domain.calendar.entity.UserCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface UserCalendarRepository extends JpaRepository<UserCalendar, Long> {
  List<UserCalendar> findByUserIdAndTargetDateBetween(Long userId, LocalDate start, LocalDate end);

  // 파트너 캘린더 조회용(2026-09-01) - 파트너가 "커플 일정으로 등록"한 것만 내 캘린더에도 보여준다.
  // 파트너의 개인 일정까지 다 보여주면 사생활 문제가 생기니 coupleEvent=true인 것만 걸러온다.
  List<UserCalendar> findByUserIdAndTargetDateBetweenAndCoupleEventTrue(Long userId, LocalDate start, LocalDate end);
}

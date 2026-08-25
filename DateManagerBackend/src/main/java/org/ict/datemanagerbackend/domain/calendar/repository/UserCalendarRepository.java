package org.ict.datemanagerbackend.domain.calendar.repository;

import org.ict.datemanagerbackend.domain.calendar.entity.UserCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserCalendarRepository extends JpaRepository<UserCalendar, Long> {
  List<UserCalendar> findByUserIdAndTargetDateBetween(Long userId, LocalDate start, LocalDate end);

  // 커플 일정 기능(2026-08-25 추가) - 본인이 작성한 일정이거나(user_id 일치), 파트너가 커플로
  // 등록해준 일정(couple_id 일치)까지 같이 조회한다. coupleId가 null(솔로 유저)이면 뒤 조건은
  // 항상 거짓이라(SQL NULL 비교는 절대 참이 안 됨) 자동으로 본인 일정만 나온다.
  @Query("SELECT c FROM UserCalendar c WHERE (c.user.id = :userId OR c.coupleId = :coupleId) AND c.targetDate BETWEEN :start AND :end")
  List<UserCalendar> findVisibleCalendars(@Param("userId") Long userId, @Param("coupleId") Long coupleId,
                                           @Param("start") LocalDate start, @Param("end") LocalDate end);
}

package org.ict.datemanagerbackend.domain.calendar.service;

import jakarta.validation.constraints.Null;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarCreateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarUpdateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Response.UserCalendarResponse;

import java.time.LocalDate;
import java.util.List;

public interface UserCalendarService {
//<<글 등록>>
UserCalendarResponse createUserCalendar(Long userId,UserCalendarCreateRequest request);
//<<글 조회>>
List<UserCalendarResponse> getMonthlyCalendars(Long userId, LocalDate start, LocalDate end);
//<<날짜 및 글 수정>>
UserCalendarResponse updateUserCalendar(Long userId,Long userCalendarId,UserCalendarUpdateRequest request);
//<<글 삭제>>
void deleteUserCalendar(Long userId, Long userCalendarId);
//<<>>
}

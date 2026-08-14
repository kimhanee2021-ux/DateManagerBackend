package org.ict.datemanagerbackend.domain.calendar.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarCreateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Response.UserCalendarResponse;
import org.ict.datemanagerbackend.domain.calendar.repository.UserCalendarRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class UserCalendarServiceImpl {

private final UserCalendarRepository userCalendarRepository;
private final UserRepository userRepository;

@Override
  public UserCalendarResponse createUserCalendar(UserCalendarCreateRequest request){

}







}

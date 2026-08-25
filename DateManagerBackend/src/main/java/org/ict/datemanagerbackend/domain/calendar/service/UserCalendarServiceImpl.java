package org.ict.datemanagerbackend.domain.calendar.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarCreateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarUpdateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Response.UserCalendarResponse;
import org.ict.datemanagerbackend.domain.calendar.entity.UserCalendar;
import org.ict.datemanagerbackend.domain.calendar.repository.UserCalendarRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
@RequiredArgsConstructor
public class UserCalendarServiceImpl implements UserCalendarService{

private final UserCalendarRepository userCalendarRepository;
private final UserRepository userRepository;

  @Override
  public UserCalendarResponse createUserCalendar(Long userId, UserCalendarCreateRequest request) {
      User user = userRepository.findById(userId).orElseThrow(()-> new RuntimeException("사용자 아이디가 없습니다."));
    UserCalendar calendar = new UserCalendar();
    calendar.setUser(user);
    calendar.setTitle(request.getTitle());
    calendar.setDescription(request.getDescription());
    calendar.setTargetDate(request.getTargetDate());
    calendar.setCourseGroupId(request.getCourseGroupId());
    //위에 엔티티 저장(userid 생성)
    UserCalendar saved = userCalendarRepository.save(calendar);

    UserCalendarResponse response = UserCalendarResponse.builder()
        .id(saved.getId())
        .title(saved.getTitle())
        .description(saved.getDescription())
        .targetDate(saved.getTargetDate())
        .courseGroupId(saved.getCourseGroupId())
        .createdAt(saved.getCreatedAt())
        .build();
    return response;
  }

  @Override
  public List<UserCalendarResponse> getMonthlyCalendars(Long userId, LocalDate start, LocalDate end) {
    List<UserCalendar> calendars = userCalendarRepository.findByUserIdAndTargetDateBetween(userId,start,end);

    return calendars.stream()
        .map(calendar -> UserCalendarResponse.builder()
            .id(calendar.getId())
            .title(calendar.getTitle())
            .description(calendar.getDescription())
            .courseGroupId(calendar.getCourseGroupId())
            .targetDate(calendar.getTargetDate())
            .createdAt(calendar.getCreatedAt())
            .build())
        .toList();
  }

  @Override
  public UserCalendarResponse updateUserCalendar(Long userId, Long userCalendarId, UserCalendarUpdateRequest request) {
    UserCalendar calendar = userCalendarRepository.findById(userCalendarId).orElseThrow(()->new NoSuchElementException("켈린더 아이디가 존재하지 않습니다."));
    // 소유권 검증 - 이게 없으면 캘린더 id만 알면 남의 일정도 수정할 수 있었다(2026-08-22 발견, 수정).
    if (!calendar.getUser().getId().equals(userId)) {
      throw new SecurityException("본인의 캘린더만 수정할 수 있습니다.");
    }
    calendar.setTitle(request.getTitle());
    calendar.setDescription(request.getDescription());
    calendar.setTargetDate(request.getTargetDate());
    calendar.setCourseGroupId(request.getCourseGroupId());
    UserCalendar saved =  userCalendarRepository.save(calendar);

    UserCalendarResponse updateCalendar = UserCalendarResponse.builder()
        .id(saved.getId())
        .createdAt(saved.getCreatedAt())
        .title(saved.getTitle())
        .description(saved.getDescription())
        .targetDate(saved.getTargetDate())
        .courseGroupId(saved.getCourseGroupId())
        .build();
    System.out.println("업데이트가 완료되었습니다.");
    return updateCalendar;
  }

  @Override
  public void deleteUserCalendar(Long userId, Long userCalendarId) {
    UserCalendar calendar = userCalendarRepository
        .findById(userCalendarId).orElseThrow(()->new NoSuchElementException("존재하지 않는 켈린더 ID 입니다."));
    // 소유권 검증 - update와 동일한 이유(2026-08-22 발견, 수정).
    if (!calendar.getUser().getId().equals(userId)) {
      throw new SecurityException("본인의 캘린더만 삭제할 수 있습니다.");
    }
    userCalendarRepository.delete(calendar);
  }
}

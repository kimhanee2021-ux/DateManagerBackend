package org.ict.datemanagerbackend.domain.calendar.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarCreateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarUpdateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Response.UserCalendarResponse;
import org.ict.datemanagerbackend.domain.calendar.entity.UserCalendar;
import org.ict.datemanagerbackend.domain.calendar.repository.UserCalendarRepository;
import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleMember;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
@RequiredArgsConstructor
public class UserCalendarServiceImpl implements UserCalendarService{

private final UserCalendarRepository userCalendarRepository;
private final UserRepository userRepository;
private final CoupleMemberRepository coupleMemberRepository;

  // CourseServiceImpl과 동일한 패턴 - 요청 바디의 coupleId를 그대로 믿지 않고, 지금 이 유저가
  // 실제로 속해있는 활성 커플을 서버가 직접 찾아서 쓴다(2026-08-25).
  private Couple findMyActiveCouple(Long userId) {
    return coupleMemberRepository.findByUser_IdAndLeftAtIsNull(userId)
        .map(CoupleMember::getCouple)
        .orElse(null);
  }

  @Override
  public UserCalendarResponse createUserCalendar(Long userId, UserCalendarCreateRequest request) {
      User user = userRepository.findById(userId).orElseThrow(()-> new RuntimeException("사용자 아이디가 없습니다."));
    UserCalendar calendar = new UserCalendar();
    calendar.setUser(user);
    calendar.setTitle(request.getTitle());
    calendar.setDescription(request.getDescription());
    calendar.setTargetDate(request.getTargetDate());
    calendar.setCourseGroupId(request.getCourseGroupId());
    if (request.isCoupleEvent()) {
      Couple couple = findMyActiveCouple(userId);
      calendar.setCoupleId(couple != null ? couple.getId() : null);
    }
    //위에 엔티티 저장(userid 생성)
    UserCalendar saved = userCalendarRepository.save(calendar);

    UserCalendarResponse response = UserCalendarResponse.builder()
        .id(saved.getId())
        .title(saved.getTitle())
        .description(saved.getDescription())
        .targetDate(saved.getTargetDate())
        .courseGroupId(saved.getCourseGroupId())
        .createdAt(saved.getCreatedAt())
        .coupleEvent(saved.getCoupleId() != null)
        .mine(true)
        .build();
    return response;
  }

  @Override
  public List<UserCalendarResponse> getMonthlyCalendars(Long userId, LocalDate start, LocalDate end) {
    Couple couple = findMyActiveCouple(userId);
    Long coupleId = couple != null ? couple.getId() : null;
    List<UserCalendar> calendars = userCalendarRepository.findVisibleCalendars(userId, coupleId, start, end);

    List<UserCalendarResponse> result = new ArrayList<>(calendars.stream()
        .map(calendar -> UserCalendarResponse.builder()
            .id(calendar.getId())
            .title(calendar.getTitle())
            .description(calendar.getDescription())
            .courseGroupId(calendar.getCourseGroupId())
            .targetDate(calendar.getTargetDate())
            .createdAt(calendar.getCreatedAt())
            .coupleEvent(calendar.getCoupleId() != null)
            .mine(calendar.getUser().getId().equals(userId))
            .build())
        .toList());

    result.addAll(buildAnniversaryEntries(couple, start, end));
    result.sort(Comparator.comparing(UserCalendarResponse::getTargetDate));
    return result;
  }

  // 커플이 등록한 "만나기 시작한 날"(Couple.metDate) 기준 100일 단위 기념일을 조회 범위 안에서
  // 계산해서 가상 일정으로 끼워 넣는다(2026-08-25 추가) - DB에 저장하지 않고 조회할 때마다
  // 계산만 하므로, 나중에 metDate가 수정돼도 자동으로 다시 맞는 날짜가 나온다.
  private List<UserCalendarResponse> buildAnniversaryEntries(Couple couple, LocalDate start, LocalDate end) {
    List<UserCalendarResponse> anniversaries = new ArrayList<>();
    if (couple == null || couple.getMetDate() == null) {
      return anniversaries;
    }
    LocalDate metDate = couple.getMetDate();
    for (int n = 1; n <= 50; n++) {
      LocalDate anniversaryDate = metDate.plusDays(n * 100L);
      if (anniversaryDate.isAfter(end)) break;
      if (!anniversaryDate.isBefore(start)) {
        anniversaries.add(UserCalendarResponse.builder()
            .id(-(n * 100L)) // 실제 캘린더 id(양수)와 안 겹치게 음수를 씀 - DB row가 아니라는 표시
            .title("💍 만난 지 " + (n * 100) + "일")
            .targetDate(anniversaryDate)
            .coupleEvent(true)
            .anniversary(true)
            .build());
      }
    }
    return anniversaries;
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
    if (request.isCoupleEvent()) {
      Couple couple = findMyActiveCouple(userId);
      calendar.setCoupleId(couple != null ? couple.getId() : null);
    } else {
      calendar.setCoupleId(null);
    }
    UserCalendar saved =  userCalendarRepository.save(calendar);

    UserCalendarResponse updateCalendar = UserCalendarResponse.builder()
        .id(saved.getId())
        .createdAt(saved.getCreatedAt())
        .title(saved.getTitle())
        .description(saved.getDescription())
        .targetDate(saved.getTargetDate())
        .courseGroupId(saved.getCourseGroupId())
        .coupleEvent(saved.getCoupleId() != null)
        .mine(true)
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

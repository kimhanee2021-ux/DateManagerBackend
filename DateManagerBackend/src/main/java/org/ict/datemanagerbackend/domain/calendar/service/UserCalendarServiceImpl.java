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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserCalendarServiceImpl implements UserCalendarService{

private final UserCalendarRepository userCalendarRepository;
private final UserRepository userRepository;
private final CoupleMemberRepository coupleMemberRepository;

  @Override
  public UserCalendarResponse createUserCalendar(Long userId, UserCalendarCreateRequest request) {
      User user = userRepository.findById(userId).orElseThrow(()-> new RuntimeException("사용자 아이디가 없습니다."));
    UserCalendar calendar = new UserCalendar();
    calendar.setUser(user);
    calendar.setTitle(request.getTitle());
    calendar.setDescription(request.getDescription());
    calendar.setTargetDate(request.getTargetDate());
    calendar.setCourseGroupId(request.getCourseGroupId());
    // 커플 연결이 안 된 사람이 coupleEvent=true를 보내는 걸 막는다 - 프론트에서도 체크박스를
    // 잠가두지만(EventFormModal), 서버가 최종 방어선이어야 한다.
    boolean isLinked = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(userId).isPresent();
    calendar.setCoupleEvent(isLinked && Boolean.TRUE.equals(request.getCoupleEvent()));
    //위에 엔티티 저장(userid 생성)
    UserCalendar saved = userCalendarRepository.save(calendar);

    UserCalendarResponse response = UserCalendarResponse.builder()
        .id(saved.getId())
        .title(saved.getTitle())
        .description(saved.getDescription())
        .targetDate(saved.getTargetDate())
        .courseGroupId(saved.getCourseGroupId())
        .createdAt(saved.getCreatedAt())
        .coupleEvent(saved.getCoupleEvent())
        .mine(true)
        .anniversary(false)
        .build();
    return response;
  }

  // 기념일 표시 기준(2026-09-01 재구현) - 커플의 만난 날짜(metDate)로부터 100일 단위(D+100,
  // D+200...)가 되는 날짜마다 가상 항목을 만든다. DB에 실제 행이 없어서 id는 targetDate 기반으로
  // 결정적으로 만들되, 진짜 캘린더 PK(양수 IDENTITY)와 절대 겹치지 않도록 음수로 만든다.
  private static final int ANNIVERSARY_INTERVAL_DAYS = 100;

  private List<UserCalendarResponse> buildAnniversaries(Couple couple, LocalDate start, LocalDate end) {
    List<UserCalendarResponse> result = new ArrayList<>();
    LocalDate metDate = couple.getMetDate();
    if (metDate == null) return result;

    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      long daysSinceMet = ChronoUnit.DAYS.between(metDate, d);
      if (daysSinceMet > 0 && daysSinceMet % ANNIVERSARY_INTERVAL_DAYS == 0) {
        result.add(UserCalendarResponse.builder()
            .id(-d.toEpochDay())
            .title("💯 만난 지 " + daysSinceMet + "일")
            .description(null)
            .targetDate(d)
            .courseGroupId(null)
            .createdAt(null)
            .coupleEvent(true)
            .mine(false)
            .anniversary(true)
            .build());
      }
    }
    return result;
  }

  @Override
  public List<UserCalendarResponse> getMonthlyCalendars(Long userId, LocalDate start, LocalDate end) {
    List<UserCalendar> myCalendars = userCalendarRepository.findByUserIdAndTargetDateBetween(userId, start, end);

    List<UserCalendarResponse> result = new ArrayList<>(myCalendars.stream()
        .map(calendar -> UserCalendarResponse.builder()
            .id(calendar.getId())
            .title(calendar.getTitle())
            .description(calendar.getDescription())
            .courseGroupId(calendar.getCourseGroupId())
            .targetDate(calendar.getTargetDate())
            .createdAt(calendar.getCreatedAt())
            .coupleEvent(calendar.getCoupleEvent())
            .mine(true)
            .anniversary(false)
            .build())
        .toList());

    // 커플로 연결돼 있으면 파트너의 커플 일정 + 기념일도 같이 내려준다(2026-09-01).
    Optional<CoupleMember> myMembership = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(userId);
    if (myMembership.isPresent()) {
      Couple couple = myMembership.get().getCouple();
      Optional<CoupleMember> partnerMembership = coupleMemberRepository.findByCoupleId(couple.getId()).stream()
          .filter(m -> m.getLeftAt() == null && !m.getUser().getId().equals(userId))
          .findFirst();

      if (partnerMembership.isPresent()) {
        Long partnerId = partnerMembership.get().getUser().getId();
        List<UserCalendar> partnerCoupleEvents =
            userCalendarRepository.findByUserIdAndTargetDateBetweenAndCoupleEventTrue(partnerId, start, end);
        result.addAll(partnerCoupleEvents.stream()
            .map(calendar -> UserCalendarResponse.builder()
                .id(calendar.getId())
                .title(calendar.getTitle())
                .description(calendar.getDescription())
                .courseGroupId(calendar.getCourseGroupId())
                .targetDate(calendar.getTargetDate())
                .createdAt(calendar.getCreatedAt())
                .coupleEvent(true)
                .mine(false)
                .anniversary(false)
                .build())
            .toList());
      }

      result.addAll(buildAnniversaries(couple, start, end));
    }

    return result;
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

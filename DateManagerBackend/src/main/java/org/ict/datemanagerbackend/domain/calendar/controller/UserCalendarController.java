package org.ict.datemanagerbackend.domain.calendar.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarCreateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarUpdateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Response.UserCalendarResponse;
import org.ict.datemanagerbackend.domain.calendar.service.UserCalendarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// 2026-08-22 - 원래 "/calendar"로 매핑돼 있었는데, SecurityConfig의 인증 필수 규칙이
// "/api/**"에만 걸려 있어서 이 경로는 로그인 없이도(anyRequest().permitAll()) 그냥 통과돼버렸다.
// 그 상태로 currentUserId()가 인증 정보 없이 authentication.getPrincipal()을 호출하면
// NPE(500)가 나는 버그가 있었음 - 다른 컨트롤러들과 동일하게 "/api" 접두사를 붙여 인증이
// 실제로 강제되게 고쳤다.
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class UserCalendarController {

  //서비스 레이어 가져오기
  private final UserCalendarService userCalendarService;

  // JwtAuthFilter가 인증된 요청의 principal에 유저 PK(Long)를 직접 넣어준다(ReportController 등
  // 다른 컨트롤러와 동일한 패턴) - 예전엔 이 값 대신 1L을 하드코딩해서, 누가 로그인하든 전부 같은
  // "user_id=1" 계정의 캘린더를 공유해버리는 버그가 있었다(2026-08-22 발견, 수정).
  private Long currentUserId(Authentication authentication) {
    return (Long) authentication.getPrincipal();
  }

  @PostMapping
  public ResponseEntity<UserCalendarResponse> createUserCalendar(Authentication authentication,
                                                                   @RequestBody UserCalendarCreateRequest userCalendarCreateRequest) {
    UserCalendarResponse userCalendarResponse = userCalendarService.createUserCalendar(currentUserId(authentication), userCalendarCreateRequest);
    return ResponseEntity.status(201).body(userCalendarResponse);
  }
  @GetMapping
  public ResponseEntity<List<UserCalendarResponse>> getUserCalendar(Authentication authentication,
                                                                      @RequestParam LocalDate start, @RequestParam LocalDate end) {
    List<UserCalendarResponse> getUserCalendar = userCalendarService.getMonthlyCalendars(currentUserId(authentication), start, end);
    return ResponseEntity.ok(getUserCalendar);
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> PutUserCalendar(Authentication authentication, @PathVariable Long id,
                                            @RequestBody UserCalendarUpdateRequest userCalendarUpdateRequest) {
    try {
      return ResponseEntity.ok(userCalendarService.updateUserCalendar(currentUserId(authentication), id, userCalendarUpdateRequest));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }
  }
  @DeleteMapping("/{id}")
  public ResponseEntity<?> DeleteUserCalendar(Authentication authentication, @PathVariable Long id){
    try {
      userCalendarService.deleteUserCalendar(currentUserId(authentication), id);
      return ResponseEntity.noContent().build();
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (SecurityException e) {
      return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }
  }
}

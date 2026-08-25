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

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor

//로그인 실패시 뜨는 오류



public class UserCalendarController {
  //서비스 레이어 가져오기
  private final UserCalendarService userCalendarService;

  // 로그인시 로그인 회원 id 가져오기
  private Long currentUserId(Authentication authentication) {
    return (Long) authentication.getPrincipal();
  }


  @PostMapping
  public ResponseEntity<UserCalendarResponse> createUserCalendar(
      Authentication authentication,
      @RequestBody UserCalendarCreateRequest request){
    UserCalendarResponse userCalendarResponse = userCalendarService
        .createUserCalendar(currentUserId(authentication),request);
      return ResponseEntity.status(201).body(userCalendarResponse);
  }

  @GetMapping
  public ResponseEntity<List<UserCalendarResponse>> getUserCalendar(
      Authentication authentication,
      @RequestParam LocalDate start,
      @RequestParam LocalDate end){
        List<UserCalendarResponse> userCalendarResponse = userCalendarService
            .getMonthlyCalendars(currentUserId(authentication),start,end);
        return ResponseEntity.ok().body(userCalendarResponse);
    }
  @PutMapping("/{userCalendarId}")
  public ResponseEntity<UserCalendarResponse> updateUserCalendar(
      Authentication authentication,
      @PathVariable Long userCalendarId,
      @RequestBody UserCalendarUpdateRequest request){
    UserCalendarResponse userCalendarResponse = userCalendarService
        .updateUserCalendar(currentUserId(authentication),userCalendarId,request);
    return ResponseEntity.ok().body(userCalendarResponse);
  }
 @DeleteMapping("/{userCalendarId}")
  public ResponseEntity<Void> deleteUserCalendar(
      Authentication authentication,
      @PathVariable Long userCalendarId){
    userCalendarService.deleteUserCalendar(currentUserId(authentication),userCalendarId);
    return ResponseEntity.status(204).build();
 }
}

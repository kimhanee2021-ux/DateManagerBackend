package org.ict.datemanagerbackend.domain.calendar.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarCreateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Request.UserCalendarUpdateRequest;
import org.ict.datemanagerbackend.domain.calendar.dto.Response.UserCalendarResponse;
import org.ict.datemanagerbackend.domain.calendar.service.UserCalendarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class UserCalendarController {

  //서비스 레이어 가져오기
  private final UserCalendarService userCalendarService;

  @PostMapping
  public ResponseEntity<UserCalendarResponse> createUserCalendar(@RequestBody UserCalendarCreateRequest userCalendarCreateRequest) {
    UserCalendarResponse userCalendarResponse = userCalendarService.createUserCalendar(1L,userCalendarCreateRequest);
  return ResponseEntity.status(201).body(userCalendarResponse);
  }
  @GetMapping
  public ResponseEntity<List<UserCalendarResponse>> getUserCalendar(@RequestParam LocalDate start,@RequestParam LocalDate end) {
    List<UserCalendarResponse> getUserCalendar = userCalendarService.getMonthlyCalendars(1L,start,end);
    return ResponseEntity.ok(getUserCalendar);
  }

  @PutMapping("/{id}")
  public ResponseEntity<UserCalendarResponse> PutUserCalendar(@PathVariable Long id, @RequestBody UserCalendarUpdateRequest userCalendarUpdateRequest) {
    UserCalendarResponse userCalendarResponse = userCalendarService.updateUserCalendar(1L,id,userCalendarUpdateRequest);
   return ResponseEntity.ok(userCalendarResponse);
  }
  @DeleteMapping("/{id}")
    public ResponseEntity<Void> DeleteUserCalendar(@PathVariable Long id){
    userCalendarService.deleteUserCalendar(1L, id);
    return ResponseEntity.noContent().build();
  }
}

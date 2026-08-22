package org.ict.datemanagerbackend.weather.controller;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.weather.dto.WeatherResponse;
import org.ict.datemanagerbackend.weather.service.WeatherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

  private final WeatherService weatherService;

  // 로그인 여부와 상관없이 홈 탭 배너에 날씨를 보여줘야 해서 SecurityConfig에서 GET만 공개로 열어둠
  // (2026-08-13, 원래는 /api/**가 기본 인증 필요라 비로그인 상태에서 이 API가 302로 막혀있었음)
  @GetMapping
  public WeatherResponse getWeather(@RequestParam double lat, @RequestParam double lon) {
    return weatherService.getCurrentWeather(lat, lon);
  }
}

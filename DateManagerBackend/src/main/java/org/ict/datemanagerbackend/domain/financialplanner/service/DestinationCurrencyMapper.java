package org.ict.datemanagerbackend.domain.financialplanner.service;

import java.util.Map;

// 목적지 원문(AI가 자연어에서 뽑은 지명) -> 통화코드 매핑(2026-08-31). 개발 명세서는 이걸 DB
// 테이블로 두라고 했지만, 국가/도시 몇십 개 수준의 고정 데이터라 코드에 상수로 두는 게 과설계를
// 피하면서도 충분하다 - 관리자 화면 없이 매핑을 늘리려면 이 파일만 고치면 된다.
public final class DestinationCurrencyMapper {

  private DestinationCurrencyMapper() {
  }

  private static final Map<String, String> DESTINATION_TO_CURRENCY = Map.ofEntries(
      Map.entry("도쿄", "JPY"), Map.entry("일본", "JPY"), Map.entry("오사카", "JPY"), Map.entry("동경", "JPY"),
      Map.entry("파리", "EUR"), Map.entry("프랑스", "EUR"), Map.entry("로마", "EUR"), Map.entry("이탈리아", "EUR"),
      Map.entry("독일", "EUR"), Map.entry("스페인", "EUR"), Map.entry("바르셀로나", "EUR"),
      Map.entry("뉴욕", "USD"), Map.entry("미국", "USD"), Map.entry("하와이", "USD"), Map.entry("괌", "USD"),
      Map.entry("런던", "GBP"), Map.entry("영국", "GBP"),
      Map.entry("방콕", "THB"), Map.entry("태국", "THB"), Map.entry("치앙마이", "THB"),
      Map.entry("다낭", "VND"), Map.entry("베트남", "VND"), Map.entry("하노이", "VND"),
      Map.entry("타이베이", "TWD"), Map.entry("대만", "TWD"),
      Map.entry("홍콩", "HKD"),
      Map.entry("싱가포르", "SGD"),
      Map.entry("발리", "IDR"), Map.entry("인도네시아", "IDR"),
      Map.entry("상하이", "CNY"), Map.entry("베이징", "CNY"), Map.entry("중국", "CNY"),
      Map.entry("호주", "AUD"), Map.entry("시드니", "AUD")
  );

  // 목적지 문자열에 매핑 키가 포함되어 있으면 그 통화코드를 반환(부분일치 - "도쿄 여행"처럼
  // 지명 뒤에 다른 말이 붙어도 잡히도록). 못 찾으면 null - 호출부가 환율 브리핑을 생략한다.
  public static String resolveCurrency(String destination) {
    if (destination == null || destination.isBlank()) return null;
    for (Map.Entry<String, String> e : DESTINATION_TO_CURRENCY.entrySet()) {
      if (destination.contains(e.getKey())) return e.getValue();
    }
    return null;
  }
}

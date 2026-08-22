package org.ict.datemanagerbackend.domain.place.service;

import java.util.List;
import java.util.Set;

/**
 * 숙박업 인허가 CSV(LODGING_STD)와 TourAPI 양쪽에서, 목포/여수 등 전라남도와 광주 일부 지역
 * 데이터의 시/도 이름이 실존하지 않는 "전남광주통합특별시"로 들어오는 문제를 바로잡는다
 * (2026-08-19, 실제 DB에서 5,840건 발견 - LODGING_STD 2,709건 + TOURAPI 3,131건).
 *
 * <p>원본 CSV(원시 바이트)를 직접 까본 결과 인코딩이 깨진 게 아니라, 원본 정부 공공데이터 자체에
 * 이 잘못된 지명이 그대로 들어있었다 - 그래서 코드에서 바로잡는 것 외엔 방법이 없다. 뒤에 오는
 * 구/군 이름을 보고 광주광역시(동구/서구/남구/북구/광산구)인지 전라남도(그 외 시/군)인지 구분한다.
 */
final class PlaceAddressNormalizer {

  // "전남광주통합특별시"가 정석이지만, 같은 원본 데이터에 "합"이 빠진 "전남광주통특별시" 변형도
  // 섞여 있었다(2026-08-19, TourAPI 재동기화 후 6건 중 1건에서 발견) - 두 변형 다 잡는다.
  private static final List<String> BROKEN_PREFIXES = List.of("전남광주통합특별시", "전남광주통특별시");
  private static final Set<String> GWANGJU_DISTRICTS = Set.of("동구", "서구", "남구", "북구", "광산구");

  private PlaceAddressNormalizer() {
  }

  static String fix(String address) {
    if (address == null) {
      return null;
    }
    String matchedPrefix = BROKEN_PREFIXES.stream().filter(address::startsWith).findFirst().orElse(null);
    if (matchedPrefix == null) {
      return address;
    }
    String rest = address.substring(matchedPrefix.length()).trim();
    String district = rest.split("\\s+", 2)[0];
    String correctSido = GWANGJU_DISTRICTS.contains(district) ? "광주광역시" : "전라남도";
    return correctSido + " " + rest;
  }
}

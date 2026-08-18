package org.ict.datemanagerbackend.domain.place.dto;

// 카카오 로컬 API(키워드로 장소 검색, https://dapi.kakao.com/v2/local/search/keyword.json)
// 응답 documents 중 places 적재에 필요한 필드만 담음. x/y는 WGS84 경도/위도가 이미 십진수
// 문자열로 내려와서(네이버처럼 10^7 스케일 정수가 아님) 그대로 파싱하면 된다.
public record KakaoLocalPlaceDto(
    String placeName,     // 업체명
    String categoryName,  // 카카오 표준 카테고리 계층 문자열 (예: "음식점 > 한식 > 국밥") - 맛집/카페 세분화용
    String addressName,   // 지번주소
    String roadAddressName, // 도로명주소
    String x,              // 경도
    String y                // 위도
) {
}

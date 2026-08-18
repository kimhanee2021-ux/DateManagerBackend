package org.ict.datemanagerbackend.domain.place.dto;

// KOPIS 박스오피스(예매율 순위, boxoffice) API 응답 <boxof> 항목 중 필요한 필드만 담음.
// mt20id는 공연목록(pblprfr) API와 동일한 값이라 이미 저장된 Place.externalId와 그대로 매칭된다.
public record KopisBoxOfficeDto(
    String mt20id,  // 공연 ID
    String prfnm,   // 공연명
    String cate,    // 장르
    String area,    // 지역
    String rnum     // 순위 (문자열로 내려와서 저장 시 Integer로 변환)
) {
}

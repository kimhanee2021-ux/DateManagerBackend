package org.ict.datemanagerbackend.domain.place.dto;

// KOPIS 공연목록 조회(pblprfr) 응답 <db> 항목 중 places 적재에 필요한 필드만 담음
public record KopisPerformanceDto(
    String mt20id,   // 공연 ID (외부 고유 식별자)
    String prfnm,    // 공연명
    String fcltynm,  // 공연시설명
    String poster,   // 포스터 이미지 URL
    String genrenm,  // 장르명
    String prfpdfrom, // 공연시작일 (yyyy.MM.dd 형식으로 내려옴) - 목록 API 응답에 이미 포함되어 있음
    String prfpdto,   // 공연종료일
    String openrun,   // 오픈런 여부 ("Y"/"N")
    String prfstate   // 공연상태 ("공연예정"/"공연중"/"공연완료")
) {
}

package org.ict.datemanagerbackend.domain.place.dto;

// KOPIS 공연 상세조회(pblprfr/{mt20id}) 응답 <db> 항목 중 places 적재에 필요한 필드만 담음.
// 목록 API(KopisPerformanceDto)와 별개로, mt10id(공연시설 ID)를 얻으려고 어차피 한 번씩 호출하던
// 이 상세조회 응답에서 공연시간/가격/예매링크까지 같이 뽑아 쓴다(추가 API 호출 없이, 2026-08-13).
public record KopisPerformanceDetailDto(
    String mt10id,        // 공연시설 ID
    String prfruntime,    // 공연시간 (예: "1시간 15분")
    String pcseguidance,  // 가격 안내 (예: "전석 50,000원", 무료공연은 "전석무료")
    String dtguidance,    // 요일별 공연시간 안내 (예: "토요일(14:00,17:00)")
    String bookingUrl     // 예매처 링크 (relates>relate>relateurl 중 첫 번째, 없을 수도 있음)
) {
}

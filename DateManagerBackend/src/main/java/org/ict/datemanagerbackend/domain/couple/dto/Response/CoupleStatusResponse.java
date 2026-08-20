package org.ict.datemanagerbackend.domain.couple.dto.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 연결 상태 조회(GET /status) 응답. 연결 안 됐으면 partner/connectedAt/metDate는 null로 내려간다.
// metDate는 connectedAt(이 사이트에서 연결된 시각)과 별개로, 두 사람이 실제로 처음 만난 날짜다.
// reconnectedByAdminAt이 null이 아니면 "관리자가 방금 다시 연결해줬는데 아직 안 봤다"는 뜻 -
// 프론트가 이 값을 보고 알림 배너를 띄운 뒤 POST /ack-admin-reconnect로 확인 처리한다.
public record CoupleStatusResponse(boolean linked, CouplePartnerDto partner, LocalDateTime connectedAt, LocalDate metDate,
                                    LocalDateTime reconnectedByAdminAt) {
}

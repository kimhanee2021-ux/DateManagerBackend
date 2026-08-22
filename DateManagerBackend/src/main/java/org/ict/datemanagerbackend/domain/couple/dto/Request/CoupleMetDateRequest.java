package org.ict.datemanagerbackend.domain.couple.dto.Request;

import java.time.LocalDate;

// 만난 날짜 등록/수정(PUT /met-date) 요청. metDate가 null이면 등록 취소(초기화)로 처리한다.
public record CoupleMetDateRequest(LocalDate metDate) {
}

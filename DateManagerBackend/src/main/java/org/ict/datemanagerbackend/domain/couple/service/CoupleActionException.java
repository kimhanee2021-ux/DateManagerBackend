package org.ict.datemanagerbackend.domain.couple.service;

import lombok.Getter;

// CoupleService가 검증 실패 시 던지는 예외 - 원래 컨트롤러가 각 검증마다 서로 다른 HTTP 상태코드
// (404/409/410/400)를 바로 반환하던 것을, 서비스로 로직을 옮기면서도 그 상태코드를 그대로 유지하기
// 위해 상태코드를 실어 나른다. 컨트롤러는 이 예외 하나만 잡아서 status()/getMessage()로 그대로
// 응답을 만든다(2026-08-19, interface+impl 전환).
@Getter
public class CoupleActionException extends RuntimeException {
  private final int status;

  public CoupleActionException(int status, String message) {
    super(message);
    this.status = status;
  }
}

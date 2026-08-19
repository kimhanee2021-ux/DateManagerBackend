package org.ict.datemanagerbackend.domain.couple.service;

import org.ict.datemanagerbackend.domain.couple.dto.Response.CoupleInviteResponse;
import org.ict.datemanagerbackend.domain.couple.dto.Response.CoupleNotificationDto;
import org.ict.datemanagerbackend.domain.couple.dto.Response.CoupleStatusResponse;
import org.ict.datemanagerbackend.domain.user.entity.User;

import java.time.LocalDate;
import java.util.List;

/**
 * 커플 초대 링크 생성/수락, 연결 상태 조회, 연결 해제를 담당하는 서비스.
 * 원래 이 로직 전체가 CoupleController 안에 직접 있었는데, admin/subscription/aichat 도메인의
 * interface+impl 패턴과 통일하기 위해 분리함(2026-08-19, 팀 회의 결정).
 *
 * <p><b>전체 흐름(state machine)</b>
 * <pre>
 *   [A: 초대 생성]                [B: 링크 수락]                  [연결 해제]
 *   CoupleInvite                  CoupleInvite.status            Couple.status
 *   status=PENDING     ---->      =ACCEPTED               ---->  =DISCONNECTED
 *   (1시간 유효)                   + Couple 1건 생성                + CoupleMember 양쪽
 *                                  + CoupleMember 2건 생성            left_at 기록
 * </pre>
 *
 * <p><b>왜 Couple/CoupleMember를 바로 안 만들고 CoupleInvite를 거치는가</b>
 * A가 초대를 누르는 시점엔 상대(B)가 누구인지 서버가 전혀 모른다(로그인조차 안 했을 수 있음).
 * 그래서 "누구든 이 토큰을 아는 사람과 연결하겠다"는 의미의 임시 티켓(CoupleInvite)만 먼저 만들고,
 * 실제로 B가 로그인한 채로 그 토큰을 들고 오는 순간(accept) 비로소 Couple/CoupleMember를 생성한다.
 * 토큰 없이 "user_id로 바로 연결" 방식을 쓰면 남의 ID를 추측해서 임의로 연결 요청을 보낼 수 있어 위험하다.
 *
 * <p>검증 실패는 전부 {@link CoupleActionException}(상태코드 포함)으로 던진다 - 컨트롤러는 그걸
 * 잡아서 그대로 HTTP 응답으로 매핑하기만 하면 된다.
 */
public interface CoupleService {
  // <<로그인한 사용자가 새 초대 토큰을 만든다. 이미 연결돼 있으면 409>>
  CoupleInviteResponse createInvite(User me);

  // <<초대 토큰을 검증하고 통과하면 Couple/CoupleMember를 실제로 생성한다. 반환값은 생성된 coupleId>>
  Long acceptInvite(User me, String token);

  // <<로그인한 유저의 현재 연결 상태(상대방 정보 포함)를 조회한다>>
  CoupleStatusResponse getStatus(User me);

  // <<실제로 처음 만난 날짜를 등록/수정한다(null이면 초기화). 연결된 커플이 없으면 404>>
  void updateMetDate(User me, LocalDate metDate);

  // <<커플 관계를 끊는다(소프트 삭제 - CoupleMember.left_at 기록 + Couple.status=DISCONNECTED)>>
  void disconnect(User me);

  // <<파트너 액션(초대 수락/만난 날짜 입력) 안 읽은 알림을 최신순으로 조회한다. 2026-08-18
  // feature/partner-notification 브랜치에서 만들었다가 2026-08-19에 이 구조로 옮겨서 합침>>
  List<CoupleNotificationDto> getUnreadNotifications(User me);

  // <<내가 갖고 있던 안 읽은 알림을 전부 읽음 처리한다>>
  void markAllNotificationsRead(User me);

  // <<관리자가 "다시 연결"해줬다는 배너를 확인 처리한다(reconnectedByAdminAt을 null로 되돌림)>>
  void ackAdminReconnect(User me);
}

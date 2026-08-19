package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateCoupleRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminCoupleDto;

import java.util.List;

public interface AdminCoupleService {
  // <<filter: all(기본) / subscribed(멤버 중 구독자 있는 커플) / free(멤버 전원 비구독) - 구독 여부 기준
  //   status: active(기본) / all - 연결 상태 기준. 기본은 해제된 예전 커플을 안 보여주되,
  //   관리자가 이력까지 보고 싶을 때는 status=all로 명시적으로 요청하게 한다.>>
  List<AdminCoupleDto> listCouples(String filter, String status);
  // <<커플 상태 변경 (ACTIVE/DISCONNECTED)>>
  AdminCoupleDto updateCouple(Long id, AdminUpdateCoupleRequest request);
  // <<커플 삭제 - 기념일/채팅/코스 등 연결 데이터가 있으면 DataIntegrityViolationException 발생>>
  void deleteCouple(Long id);
}

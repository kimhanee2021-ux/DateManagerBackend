package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateCoupleRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminCoupleDto;

import java.util.List;

public interface AdminCoupleService {
  // <<filter: all(기본) / subscribed(멤버 중 구독자 있는 커플) / free(멤버 전원 비구독)>>
  List<AdminCoupleDto> listCouples(String filter);
  // <<커플 상태 변경 (ACTIVE/DISCONNECTED)>>
  AdminCoupleDto updateCouple(Long id, AdminUpdateCoupleRequest request);
  // <<커플 삭제 - 기념일/채팅/코스 등 연결 데이터가 있으면 DataIntegrityViolationException 발생>>
  void deleteCouple(Long id);
}

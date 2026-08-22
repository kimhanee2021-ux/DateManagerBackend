package org.ict.datemanagerbackend.domain.aichat.repository;

import org.ict.datemanagerbackend.domain.aichat.entity.AiChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

  // findByIdAndUser_Id(Long, Long) -> "WHERE id = ? AND user_id = ?"
  // 세션 id만으로 조회하면 다른 유저의 세션 id를 추측해서 남의 대화를 보거나 이어 쓸 수 있어서,
  // 항상 "이 세션이 실제로 이 유저 것이 맞는지"까지 같이 확인한다(CoupleController의 소유권 검증과 같은 이유).
  Optional<AiChatSession> findByIdAndUser_Id(Long id, Long userId);

  // "지난 대화 보기" 목록용(2026-08-22 추가). 쓰다 보면 세션이 계속 쌓이니 15개씩 페이지네이션-
  // 정렬은 컨트롤러의 @PageableDefault(sort="createdAt", direction=DESC)로 지정한다.
  Page<AiChatSession> findByUser_Id(Long userId, Pageable pageable);

}

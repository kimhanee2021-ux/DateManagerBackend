package org.ict.datemanagerbackend.domain.aichat.repository;

import org.ict.datemanagerbackend.domain.aichat.entity.AiChatMessageIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AiChatMessageIntentRepository extends JpaRepository<AiChatMessageIntent, Long> {

  // "안 쓰이던 의도 태그 재활용"(2026-08-25) - 분석 시점에 저장만 되고 아무도 안 읽던 데이터를
  // 처음으로 조회해서 쓴다. 결과는 [intentTag, 건수] 튜플을 건수 내림차순으로.
  @Query("SELECT i.intentTag, COUNT(i) FROM AiChatMessageIntent i "
      + "WHERE i.message.sender.id = :userId GROUP BY i.intentTag ORDER BY COUNT(i) DESC")
  List<Object[]> countIntentTagsByUserId(@Param("userId") Long userId);
}

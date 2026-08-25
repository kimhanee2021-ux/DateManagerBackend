package org.ict.datemanagerbackend.domain.aichat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ict.datemanagerbackend.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;

// 생성 시점에 고정되는 메시지 로그라 생성 이후 값이 바뀌지 않아 setter가 없다.
@Entity
@Table(name = "ai_chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class AiChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // 메시지 ID (PK)

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false)
  private AiChatSession session; // 세션 (ai_chat_sessions.id 참조)

  @Column(name = "sender_type", nullable = false, length = 20)
  private String senderType; // 발신 주체 (USER, AI)

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sender_id")
  private User sender; // 발신 유저 (users.id 참조, AI 발신이면 null)

  @Lob
  @Column(name = "message_text", nullable = false)
  private String messageText; // 메시지 내용 (CLOB 매핑)

  // AiChatSession과 같은 이유로 insertable=false 대신 @Builder.Default 사용(2026-08-13 수정).
  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now(); // 생성 일시

  // 대화 중 후속 질문 추천(2026-08-22 추가) - DB엔 저장 안 하고, 방금 받은 AI 응답 하나에만 실어서
  // 그 턴의 컨트롤러 응답에 얹어 보낸다(과거 이력을 다시 불러올 땐 새로 안 만들고 비워둠).
  @Transient
  @Setter
  private List<String> followUpQuestions;

  // 이번 턴에서 대화 내용을 분석해 UserStyle이 실시간으로 갱신된 축이 있으면 담는다(2026-08-25
  // 추가) - followUpQuestions와 같은 이유로 DB엔 저장 안 하고 그 턴의 응답에만 실어 보낸다.
  // 예전엔 이 갱신이 화면에 전혀 안 보이고 조용히 백그라운드에서만 일어났다.
  @Transient
  @Setter
  private List<String> updatedStyleAxes;

}

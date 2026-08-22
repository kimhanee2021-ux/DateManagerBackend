package org.ict.datemanagerbackend.domain.aichat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.user.entity.User;
import java.time.LocalDateTime;

// title은 세션 이름 변경이 가능하고, isActive는 세션 종료 시 갱신되는 값이라 개별 setter를 열어둔다.
@Entity
@Table(name = "ai_chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class AiChatSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // 채팅 세션 ID (PK)

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user; // 유저 (users.id 참조)

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "couple_id")
  private Couple couple; // 커플 (couples.id 참조)

  @Setter
  @Column(name = "title", length = 100)
  private String title; // 세션 타이틀

  // OpenAI Responses API(https://api.openai.com/v1/responses)가 대화 맥락을 서버 쪽에서 기억해주는
  // 방식으로 바뀌면서(2026-08-14, 교육 자료 갱신분 반영) 추가된 컬럼. 매 요청마다 이 값을 함께
  // 보내면 OpenAI가 지난 대화 전체를 이어서 기억해주기 때문에, 우리가 매번 메시지 이력을 통째로
  // 다시 만들어 보낼 필요가 없어졌다. 세션 첫 메시지 전까지는 null.
  @Setter
  @Column(name = "last_response_id", length = 100)
  private String lastResponseId;

  // place_styles와 같은 이유(DB에 실제 DEFAULT가 없어 insertable=false만으로는 ORA-01400 발생)로
  // insertable=false 대신 @Builder.Default로 애플리케이션이 직접 초기값을 채우도록 함(2026-08-13 수정).
  @Builder.Default
  @Column(name = "session_type", nullable = false, updatable = false)
  private String sessionType = "PLANNER"; // 세션 구분

  @Builder.Default
  @Column(name = "is_private", nullable = false, updatable = false)
  private Integer isPrivate = 0; // 비밀 비서 플래그 (0/1)

  @Setter
  @Builder.Default
  @Column(name = "is_active", nullable = false)
  private Integer isActive = 1; // 활성화 여부 (0/1, 생성 시 1, 세션 종료 시 앱이 0으로 갱신)

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now(); // 생성 일시

  @Builder.Default
  @Column(name = "updated_at", nullable = false, updatable = false)
  private LocalDateTime updatedAt = LocalDateTime.now(); // 수정 일시

}

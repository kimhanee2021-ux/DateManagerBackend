package org.ict.datemanagerbackend.domain.couple.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ict.datemanagerbackend.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * "파트너가 뭔가를 했다"는 걸 다음 로그인 때 알려주기 위한 알림 한 건.
 * 예: 파트너가 초대를 수락했을 때(PARTNER_ACCEPTED), 파트너가 만난 날짜를 입력했을 때
 * (PARTNER_MET_DATE_SET) - 이벤트가 발생한 그 순간 상대방(recipient)이 접속해 있지 않을 수도
 * 있으므로, "누구에게 어떤 알림을 언제 보냈고 아직 안 읽었는지"를 DB에 기록해뒀다가
 * 다음 로그인 시 CoupleController의 /notifications/unread로 몰아서 보여준다.
 * (2026-08-18 feature/partner-notification 브랜치에서 작성, 2026-08-19에 CoupleService
 * 구조로 옮겨서 main에 병합.)
 */
@Entity
@Table(name = "couple_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CoupleNotification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 알림을 받는 사람. 이벤트를 발생시킨 사람(예: 만난 날짜를 입력한 사람) 본인이 아니라
  // 그 상대방이 recipient가 된다.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recipient_id", nullable = false)
  private User recipient;

  // "PARTNER_ACCEPTED" | "PARTNER_MET_DATE_SET" - 프론트에서 아이콘/문구를 분기할 수 있게
  // 남겨두지만, 지금은 message를 그대로 보여주는 용도로만 쓴다.
  @Column(nullable = false)
  private String type;

  // 프론트에 그대로 노출할 한국어 메시지. 생성 시점에 백엔드가 미리 완성해서 저장해두면
  // 프론트는 타입별 문구 분기 로직 없이 그냥 렌더링만 하면 된다.
  @Column(nullable = false)
  private String message;

  @Setter
  @Builder.Default
  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @Builder.Default
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

}

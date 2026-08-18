package org.ict.datemanagerbackend.domain.subscription.entity;

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

// 예전엔 클래스 전체에 @Setter가 붙어있어서 setStatus()가 컴파일도 되고 호출도 됐지만,
// status 컬럼이 updatable=false라 실제로는 DB에 저장이 안 되고 조용히 무시되는 버그가 있었다
// (Couple.status에서 발견됐던 것과 동일한 패턴). status의 updatable만 풀고, 나머지는
// 실제로 값이 바뀌어야 하는 필드에만 개별 @Setter를 붙이는 방식으로 정리했다.
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class Subscription {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // 구독 고유 ID (PK)

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user; // 회원 (users.id 참조)

  @Setter
  @Column(name = "plan_code", nullable = false)
  private String planCode; // 구독 플랜 코드 (FREE, PREMIUM_MONTHLY 등) - 업그레이드/다운그레이드 시 변경됨

  // 주의: subscriptions 테이블도 ddl-auto로만 생성돼 DB 레벨 DEFAULT가 없다(User.createdAt과 동일한 이유).
  // insertable=false로 DB 기본값에 의존했던 건 실제로는 항상 null/에러가 나는 버그였어서 @Builder.Default로 교체했다.
  @Setter
  @Builder.Default
  @Column(nullable = false)
  private String status = "ACTIVE"; // 구독 상태 (ACTIVE, PAST_DUE-결제실패, CANCELED) - 결제 성공/실패/해지 시 변경됨

  @Builder.Default
  @Column(name = "started_at", nullable = false)
  private LocalDateTime startedAt = LocalDateTime.now(); // 구독 시작일

  @Setter
  @Column(name = "expires_at")
  private LocalDateTime expiresAt; // 구독 만료일 - 갱신/연장 시 변경됨

  @Setter
  @Column(name = "payment_provider")
  private String paymentProvider; // 결제 수단/제공자 (TOSS 등)

  @Column(name = "billing_key")
  private String billingKey; // PG(토스페이먼츠)에서 발급받은 빌링키 - 카드 최초 등록 후 저장, 이후 정기결제에 사용

  @Column(name = "customer_key")
  private String customerKey; // 빌링키 발급 시 사용한 토스 customerKey - 결제 승인 요청 시 반드시 동일한 값을 써야 해서 같이 저장

  @Setter
  @Column(name = "last_payment_status")
  private String lastPaymentStatus; // 가장 최근 결제 시도 결과 (SUCCESS, FAILED) - 결제/갱신마다 갱신됨

  @Setter
  @Column(name = "last_payment_error", length = 500)
  private String lastPaymentError; // 결제 실패 시 토스가 내려준 에러 메시지 (프론트에 그대로 표시) - 결제 실패마다 갱신됨

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now(); // 생성 일시

}

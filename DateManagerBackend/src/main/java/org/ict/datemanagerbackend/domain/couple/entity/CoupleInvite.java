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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ict.datemanagerbackend.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * 커플 초대 링크(토큰) 하나하나의 상태를 저장하는 엔티티.
 *
 * <p>기존 27개 테이블 DDL({@code project-date-manager-schema-fk} 메모리 참고)엔 이 테이블이
 * 없었다 — "초대"라는 개념 자체가 원래 설계에 없었기 때문. 그래서 수동 SQL이 아니라
 * application.yaml의 {@code ddl-auto: update}(Hibernate가 엔티티를 보고 스키마를 알아서 갱신하는 옵션)로
 * 새로 생성된다. 즉 이 클래스를 만든 것 자체가 곧 "couple_invites 테이블을 만든 것"과 같다.
 *
 * <p><b>왜 Couple/CoupleMember와 별도 테이블이어야 하는가</b><br>
 * Couple은 "이미 완성된 관계", User는 "고정된 신원"만 표현하는데, 초대는 그 둘 어디에도 속하지 않는
 * "아직 완성되지 않았고, 시간이 지나면 무효가 되며, 여러 번 재시도할 수 있는 임시 요청" 상태이기 때문에
 * 별도의 생명주기(PENDING → ACCEPTED 또는 EXPIRED)를 갖는 자기만의 테이블이 필요하다.
 *
 * <p>생성/수락 로직은
 * {@link org.ict.datemanagerbackend.domain.couple.controller.CoupleController}의
 * createInvite() / acceptInvite()를 참고.
 */
// status/acceptedBy/couple은 초대가 수락/만료될 때 갱신되는 값이라 setter를 열어둔다.
// token/inviter/expiresAt/createdAt은 생성된 이후 절대 바뀌지 않는 값이라 setter가 없다(불변).
@Entity
@Table(
    name = "couple_invites",
    uniqueConstraints = {
        // 토큰은 이 테이블 안에서 유일해야 한다 - 같은 토큰이 두 번 발급되면 어느 초대를 가리키는지
        // 알 수 없게 되어 findByToken() 결과가 여러 개가 될 위험이 있다.
        @UniqueConstraint(name = "uq_couple_invites_token", columnNames = "token")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class CoupleInvite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // 초대 ID (PK) - 내부 관리용. 외부(프론트)에는 절대 노출하지 않고 token만 노출한다.

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inviter_user_id", nullable = false)
  private User inviter; // 초대를 만든 사람(A)

  @Column(nullable = false, length = 64)
  private String token; // 초대 링크에 들어가는 무작위 토큰(UUID, 36자). 이 값을 아는 사람만 수락할 수 있다.

  @Setter
  @Column(nullable = false, length = 20)
  private String status; // PENDING(대기) -> ACCEPTED(수락 완료) 또는 EXPIRED(시간 초과)

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt; // 만료 시각 (생성 시점 + 1시간). 이 시각이 지나면 acceptInvite()가 거부한다.

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt; // 초대 생성 시각

  // ↓ 아래 두 필드는 "수락되기 전"에는 항상 null이고, acceptInvite()가 성공했을 때만 채워진다.
  // 즉 이 필드들의 값 유무 자체가 "이 초대가 실제로 성사됐는지"를 보여주는 이력 데이터 역할도 한다.

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "accepted_by_user_id")
  private User acceptedBy; // 수락한 사람(B) - 수락 전엔 null

  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "couple_id")
  private Couple couple; // 수락으로 새로 생성된 커플 - 수락 전엔 null

}

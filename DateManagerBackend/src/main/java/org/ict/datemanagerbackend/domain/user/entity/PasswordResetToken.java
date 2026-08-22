package org.ict.datemanagerbackend.domain.user.entity;

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

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 링크에 담기는 토큰 하나하나의 상태를 저장한다.
 *
 * <p>{@link org.ict.datemanagerbackend.domain.couple.entity.CoupleInvite}와 같은 패턴 - "아직 완성되지
 * 않았고, 시간이 지나면 무효가 되며, 한 번만 쓸 수 있는 임시 요청"이라 User에 컬럼을 얹지 않고 별도
 * 테이블로 뺐다. 이렇게 하면 같은 계정이 재설정을 여러 번 요청해도 예전 토큰들의 이력이 그대로 남는다.
 */
// token/user/expiresAt/createdAt은 생성 이후 절대 바뀌지 않는다(불변). used만 재설정 완료 시 갱신된다
// - 재설정 API가 이미 used=true인 토큰을 다시 쓰지 못하게 막는 데 쓰인다(링크 재사용 방지).
@Entity
@Table(
    name = "password_reset_tokens",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_password_reset_tokens_token", columnNames = "token")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 64)
  private String token; // 재설정 링크에 그대로 들어가는 무작위 값(UUID)

  @Setter
  @Column(nullable = false)
  private boolean used; // true면 이미 이 토큰으로 비밀번호를 재설정했다는 뜻 - 같은 링크 재사용 방지

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt; // 생성 시점 + 30분

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

}

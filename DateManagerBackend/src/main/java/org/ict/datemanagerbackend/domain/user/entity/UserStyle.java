package org.ict.datemanagerbackend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 온보딩 점수는 applyOnboarding()을 통해 6축을 한 번에 갱신한다. 축 하나만 빠진 불완전한 상태로
// 저장되는 것을 막기 위해 개별 setter는 열지 않는다. user는 users.id와 PK를 공유하는 1:1 관계다.
@Entity
@Table(name = "user_styles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 스펙상 기본 생성자 필수 (무분별한 객체 생성 방지)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class UserStyle {

  // PlaceStyle과 같은 이유로 @MapsId 패턴으로 전환(2026-08-13) - Spring Data JPA가 "@Id를 연관관계에
  // 직접 붙인" 엔티티는 Repository 생성 시 "IdClass가 없다"고 오인해서 실패하는 문제가 있었다.
  @Id
  @Column(name = "user_id")
  private Long userId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user; // users.id와 PK를 공유하는 1:1 관계

  @Column(name = "init_energy")
  private Integer initEnergy;

  @Column(name = "init_immersion")
  private Integer initImmersion;

  @Column(name = "init_vibe")
  private Integer initVibe;

  @Column(name = "init_aesthetic")
  private Integer initAesthetic;

  @Column(name = "init_pacing")
  private Integer initPacing;

  @Column(name = "init_depth")
  private Integer initDepth;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  public static UserStyle create(User user) {
    return UserStyle.builder()
        .user(user)
        .build();
  }

  public void applyOnboarding(int energy, int immersion, int vibe,
                              int aesthetic, int pacing, int depth) {
    this.initEnergy = energy;
    this.initImmersion = immersion;
    this.initVibe = vibe;
    this.initAesthetic = aesthetic;
    this.initPacing = pacing;
    this.initDepth = depth;
  }

}

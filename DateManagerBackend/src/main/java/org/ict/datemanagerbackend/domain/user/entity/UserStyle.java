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
//
// 2026-08-22 - 필드를 Integer에서 Double로 바꿨다(USER_STYLES.INIT_* 컬럼도 NUMBER(10,0)에서
// NUMBER(14,4)로 한 번 ALTER함, ddl-auto는 기존 컬럼 타입을 안 바꿔주므로 수동으로 넓힘). 실시간
// 갱신 엔진(UserStyleUpdateService)이 매번 정수로 반올림해서 저장하면, 신호가 약해서 delta가
// 0.5 미만인 축은 반올림값이 매번 0이 되어 "영원히 안 움직이는" 축이 생기는 문제가 실측으로
// 확인됐다(예: current=50, target=70, α=0.02 → delta=0.4, 몇 번을 반복해도 그대로 50). 내부
// 저장값은 소수점을 계속 누적해서 진행분이 안 사라지게 하고, 화면/API로 나가는 값만
// round(Integer)로 정수화한다(round() 헬퍼 참고).
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
  private Double initEnergy;

  @Column(name = "init_immersion")
  private Double initImmersion;

  @Column(name = "init_vibe")
  private Double initVibe;

  @Column(name = "init_aesthetic")
  private Double initAesthetic;

  @Column(name = "init_pacing")
  private Double initPacing;

  @Column(name = "init_depth")
  private Double initDepth;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  public static UserStyle create(User user) {
    return UserStyle.builder()
        .user(user)
        .build();
  }

  public void applyOnboarding(int energy, int immersion, int vibe,
                              int aesthetic, int pacing, int depth) {
    this.initEnergy = (double) energy;
    this.initImmersion = (double) immersion;
    this.initVibe = (double) vibe;
    this.initAesthetic = (double) aesthetic;
    this.initPacing = (double) pacing;
    this.initDepth = (double) depth;
  }

  // 온보딩(applyOnboarding)은 6축을 한꺼번에만 바꾸게 막아뒀지만, 이건 목적이 다르다 - 유저의
  // 행동(장소 열람, AI챗 언급 등)에 따라 축 하나씩 조금씩 갱신하는 실시간 갱신 엔진
  // (UserStyleUpdateService) 전용 진입점이다. 축 하나만 갱신되는 게 여기서는 정상 동작이라
  // applyOnboarding과 같은 "불완전 저장 방지" 제약을 걸 이유가 없다.
  public Double getAxisScore(String axis) {
    return switch (axis) {
      case "ENERGY" -> initEnergy;
      case "IMMERSION" -> initImmersion;
      case "VIBE" -> initVibe;
      case "AESTHETIC" -> initAesthetic;
      case "DEPTH" -> initDepth;
      default -> throw new IllegalArgumentException("알 수 없는 성향 축: " + axis);
    };
  }

  public void setAxisScore(String axis, double value) {
    switch (axis) {
      case "ENERGY" -> initEnergy = value;
      case "IMMERSION" -> initImmersion = value;
      case "VIBE" -> initVibe = value;
      case "AESTHETIC" -> initAesthetic = value;
      case "DEPTH" -> initDepth = value;
      default -> throw new IllegalArgumentException("알 수 없는 성향 축: " + axis);
    }
  }

  // 화면/API로 내보낼 때만 쓰는 반올림 헬퍼 - 저장은 소수점 그대로, 노출은 항상 정수.
  public static Integer round(Double value) {
    return value == null ? null : (int) Math.round(value);
  }

}

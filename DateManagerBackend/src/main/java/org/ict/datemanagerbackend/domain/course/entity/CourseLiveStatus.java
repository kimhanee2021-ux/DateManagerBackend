package org.ict.datemanagerbackend.domain.course.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// isLiveActive/currentMoodMode/lastLatitude/lastLongitude는 라이브 모드 진행 중 계속 갱신되는
// 값이라 개별 setter를 열어둔다. courseGroup은 1:1 PK 공유라 불변.
@Entity
@Table(name = "course_live_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class CourseLiveStatus {

  // PlaceStyle과 같은 이유로 @MapsId 패턴으로 전환(2026-08-13) - Spring Data JPA가 "@Id를 연관관계에
  // 직접 붙인" 엔티티는 Repository 생성 시 "IdClass가 없다"고 오인해서 실패하는 문제가 있었다.
  @Id
  @Column(name = "course_group_id")
  private Long courseGroupId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "course_group_id")
  private CourseGroup courseGroup; // 코스 그룹과 PK를 공유하는 1:1 관계

  @Setter
  @Column(name = "is_live_active", nullable = false, insertable = false)
  private Integer isLiveActive; // 생성 시 DB 기본값 0, 이후 라이브 모드 시작/종료 로직이 갱신

  @Setter
  @Column(name = "current_mood_mode", nullable = false)
  private String currentMoodMode;

  @Setter
  @Column(name = "last_latitude")
  private Double lastLatitude;

  @Setter
  @Column(name = "last_longitude")
  private Double lastLongitude;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime updatedAt;
}

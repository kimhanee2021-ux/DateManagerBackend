package org.ict.datemanagerbackend.domain.place.entity;

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
import lombok.Setter;

import java.time.LocalDateTime;

// waitingStatus/waitingTeams/reservationType/priceText/parkingInfo는 실시간 동기화·정보 갱신으로
// 계속 바뀌는 값이라 개별 setter를 열어둔다. place는 1:1 PK 공유라 불변.
// 주의: place_realities 테이블도 ddl-auto로만 생성돼 DB 레벨 DEFAULT가 없다(Place.createdAt과 같은 이유).
// insertable=false로 "DB 기본값을 쓴다"고 가정했던 필드들은 실제로 채워줄 기본값이 없어 INSERT 시
// NOT NULL 위반(ORA-01400)이 난다. 애플리케이션이 초기값을 직접 채우도록 @Builder.Default로 바꿨다.
@Entity
@Table(name = "place_realities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class PlaceReality {

  // PlaceStyle과 같은 이유로 @MapsId 패턴으로 전환(2026-08-13) - Spring Data JPA가 "@Id를 연관관계에
  // 직접 붙인" 엔티티는 Repository 생성 시 "IdClass가 없다"고 오인해서 실패하는 문제가 있었다.
  @Id
  @Column(name = "place_id")
  private Long placeId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "place_id")
  private Place place; // 장소와 PK를 공유하는 1:1 관계

  @Setter
  @Builder.Default
  @Column(name = "waiting_status", nullable = false)
  private String waitingStatus = "NONE"; // 웨이팅 상태 (생성 시 기본값 'NONE', 이후 실시간 동기화 로직이 갱신)

  @Setter
  @Builder.Default
  @Column(name = "waiting_teams", nullable = false)
  private Integer waitingTeams = 0; // 현재 대기 팀 수 (생성 시 기본값 0, 이후 실시간 동기화 로직이 갱신)

  @Setter
  @Builder.Default
  @Column(name = "reservation_type", nullable = false)
  private String reservationType = "WALKIN"; // 예약 방식 (생성 시 기본값 'WALKIN', 이후 실시간 동기화 로직이 갱신)

  @Setter
  @Column(name = "price_text", length = 50)
  private String priceText; // 가격대 안내 태그

  @Setter
  @Column(name = "parking_info", length = 100)
  private String parkingInfo; // 주차 정보

  @Builder.Default
  @Column(name = "updated_at", nullable = false, updatable = false)
  private LocalDateTime updatedAt = LocalDateTime.now(); // 수정 일시

}

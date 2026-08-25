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

// score_*/is_indoor/is_activity는 큐레이션 로직이 생성 이후 계속 갱신하는 값이라 개별 setter를 열어둔다.
// 주의: place_styles 테이블도 ddl-auto로만 생성돼 DB 레벨 DEFAULT가 없다(Place.createdAt과 같은 이유).
// 예전엔 "생성 시 DB 기본값을 쓴다"고 insertable=false로 막아뒀지만 실제로는 DB에 채워줄 기본값이 없어
// INSERT 시 NOT NULL 위반(ORA-01400)이 난다. 그래서 애플리케이션이 초기값을 직접 채우도록
// @Builder.Default로 바꿨다 - 큐레이션 로직이 아직 없을 때 쓸 "중립값"들이다.
@Entity
@Table(name = "place_styles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class PlaceStyle {

  // Spring Data JPA가 "@Id를 연관관계에 직접 붙인" 엔티티는 Repository를 만들 때
  // "IdClass가 없다"고 오인해서 실패하는 문제가 있어(2026-08-13 PlaceStyleRepository 생성 중 발견),
  // JPA 표준 "공유 기본키(shared primary key)" 패턴인 @MapsId로 바꿨다 - 실제 DB 컬럼(place_id)은 그대로.
  @Id
  @Column(name = "place_id")
  private Long placeId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "place_id")
  private Place place; // 장소와 PK를 공유하는 1:1 관계

  @Setter
  @Builder.Default
  @Column(name = "score_energy", nullable = false)
  private Integer scoreEnergy = 50; // 에너지 성향 점수 (생성 시 중립값 50, 이후 큐레이션 로직이 갱신)

  @Setter
  @Builder.Default
  @Column(name = "score_immersion", nullable = false)
  private Integer scoreImmersion = 50; // 몰입 성향 점수 (생성 시 중립값 50, 이후 큐레이션 로직이 갱신)

  @Setter
  @Builder.Default
  @Column(name = "score_vibe", nullable = false)
  private Integer scoreVibe = 50; // 분위기 성향 점수 (생성 시 중립값 50, 이후 큐레이션 로직이 갱신)

  @Setter
  @Builder.Default
  @Column(name = "score_aesthetic", nullable = false)
  private Integer scoreAesthetic = 50; // 미감 성향 점수 (생성 시 중립값 50, 이후 큐레이션 로직이 갱신)

  @Setter
  @Builder.Default
  @Column(name = "score_depth", nullable = false)
  private Integer scoreDepth = 50; // 깊이 성향 점수 (생성 시 중립값 50, 이후 큐레이션 로직이 갱신)

  // nullable=false를 안 쓰는 이유는 PlaceCategory.scorePacing과 동일(기존 행이 있는 테이블에
  // NOT NULL 컬럼을 ddl-auto로 추가하면 ORA-01758로 실패함).
  @Setter
  @Builder.Default
  @Column(name = "score_pacing")
  private Integer scorePacing = 50; // 즉흥·계획 성향 점수(2026-08-20 추가, 생성 시 중립값 50)

  @Setter
  @Builder.Default
  @Column(name = "is_indoor", nullable = false)
  private Integer isIndoor = 1; // 실내 여부 (0/1, 생성 시 기본값 1, 이후 큐레이션 로직이 갱신)

  @Setter
  @Builder.Default
  @Column(name = "is_activity", nullable = false)
  private Integer isActivity = 0; // 액티비티 여부 (0/1, 생성 시 기본값 0, 이후 큐레이션 로직이 갱신)

  @Builder.Default
  @Column(name = "updated_at", nullable = false, updatable = false)
  private LocalDateTime updatedAt = LocalDateTime.now(); // 수정 일시

  // 리뷰 기반 장소별 점수 보정(2026-08-25 착수) - place_category가 연결된 장소는 원래 카테고리
  // 공통값을 그대로 썼는데(PlaceMatchServiceImpl.resolvePlaceScores), 같은 세부분류라도 실제
  // 장소마다 다르다는 요구로 개별 리뷰 검색을 거쳐 이 행이 실제로 채워지면(reviewed=true) 그때부터
  // 카테고리 공통값보다 이 행을 우선한다. false인 동안은 지금처럼 카테고리 값이 그대로 쓰인다.
  // nullable=false를 안 쓰는 이유는 위 scorePacing과 동일 - place_styles에 이미 4만여 행이 있는
  // 상태에서 ddl-auto가 NOT NULL로 컬럼을 추가하면 오라클이 ORA-01758로 거부한다(2026-08-25,
  // 실제로 이 실수를 했다가 백엔드가 기동 실패해서 확인함).
  @Setter
  @Builder.Default
  @Column(name = "reviewed")
  private Boolean reviewed = false;

  // 리뷰 검색 결과를 요약한 값이라 정확한 실측치가 아니라 AI가 웹 검색 스니펫에서 종합 추정한
  // 값이다(참고용) - 5점 만점.
  @Setter
  @Column(name = "rating")
  private Double rating;

  @Setter
  @Column(name = "review_count")
  private Integer reviewCount;

}

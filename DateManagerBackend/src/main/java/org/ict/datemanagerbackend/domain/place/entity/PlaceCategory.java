package org.ict.datemanagerbackend.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// "카테고리(대분류) -> 세부분류 -> 6대 성향 점수" 매핑을 코드가 아니라 데이터로 관리하기 위한 참조
// 테이블. 예: (맛집, 술집/포차/이자카야) 행 하나가 이모지 하나 + 성향 점수 5개를 들고 있고,
// Place.placeCategory가 이 행을 가리키면 그 장소의 성향점수가 되는 방식(2026-08-14).
// 점수를 조정할 때 자바 코드를 안 고치고 이 테이블 값만 바꾸면 되게 하려는 목적.
@Entity
@Table(
    name = "place_categories",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_place_categories", columnNames = {"parent_category", "sub_category"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class PlaceCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Setter
  @Column(name = "parent_category", nullable = false, length = 50)
  private String parentCategory; // 대분류 (맛집, 공연, 숙박, 전시 등 - Place.category와 동일한 값)

  @Setter
  @Column(name = "sub_category", nullable = false, length = 50)
  private String subCategory; // 세부분류 (예: "술집/포차/이자카야", "이머시브 연극")

  @Setter
  @Column(name = "emoji", length = 10)
  private String emoji;

  @Setter
  @Builder.Default
  @Column(name = "score_energy", nullable = false)
  private Integer scoreEnergy = 50;

  @Setter
  @Builder.Default
  @Column(name = "score_immersion", nullable = false)
  private Integer scoreImmersion = 50;

  @Setter
  @Builder.Default
  @Column(name = "score_vibe", nullable = false)
  private Integer scoreVibe = 50;

  @Setter
  @Builder.Default
  @Column(name = "score_aesthetic", nullable = false)
  private Integer scoreAesthetic = 50;

  @Setter
  @Builder.Default
  @Column(name = "score_depth", nullable = false)
  private Integer scoreDepth = 50;

  @Setter
  @Builder.Default
  @Column(name = "is_indoor", nullable = false)
  private Integer isIndoor = 1; // 실내 여부 (0/1)

  @Setter
  @Builder.Default
  @Column(name = "is_activity", nullable = false)
  private Integer isActivity = 0; // 액티비티 여부 (0/1)

}

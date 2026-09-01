package org.ict.datemanagerbackend.domain.financialplanner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// AI 스마트 자금 플래너의 목표 하나. (ownerType, ownerId) 조합당 "진행 중인 목표는 항상 1개"만
// 유지한다(2026-08-31 결정) - 새 목표를 자연어로 다시 말하면 기존 행을 새 내용으로 덮어쓰는
// 방식으로 "수정"을 구현한다(FinancialPlannerServiceImpl.createOrUpdateGoal() 참고), 별도의
// 수정 전용 엔드포인트는 두지 않는다.
//
// ownerId는 owner_type=SOLO면 users.id, COUPLE이면 couples.id를 가리킨다 - 두 값의 출처 테이블이
// 다르므로 FK 제약은 걸지 않고(걸면 어느 쪽 테이블을 참조할지 고정되어 버림) 애플리케이션 레벨에서만
// 의미를 지킨다.
@Entity
@Table(name = "fund_goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FundGoal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "owner_type", nullable = false, length = 10)
  private OwnerType ownerType;

  @Setter
  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Setter
  @Column(nullable = false, length = 100)
  private String title;

  @Setter
  @Column(name = "target_amount", nullable = false)
  private Long targetAmount;

  @Setter
  @Builder.Default
  @Column(name = "current_amount", nullable = false)
  private Long currentAmount = 0L;

  @Setter
  @Column(name = "target_period_month", nullable = false)
  private Integer targetPeriodMonth;

  @Setter
  @Column(length = 30)
  private String category;

  // "도쿄"/"일본"/"동경"처럼 AI가 자연어에서 뽑아낸 목적지 원문. null이면 여행 목적이 아니거나
  // 목적지를 못 뽑은 경우 - 이때 대시보드 응답에서 환율 브리핑 섹션 자체를 생략한다.
  @Setter
  @Column(name = "destination_country", length = 50)
  private String destinationCountry;

  // AI 브리핑 코멘트 캐시(2026-08-31) - 목표 데이터가 그대로면 매 대시보드 조회마다 다시 생성하지
  // 않고 하루 1번만 재생성한다(비용 절감, 명세서 3-4 보완사항).
  @Setter
  @Column(name = "ai_comment", length = 500)
  private String aiComment;

  @Setter
  @Column(name = "ai_comment_generated_at")
  private LocalDateTime aiCommentGeneratedAt;

  @Setter
  @Builder.Default
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Setter
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}

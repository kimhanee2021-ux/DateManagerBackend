package org.ict.datemanagerbackend.domain.financialplanner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDateTime;

// finlife(금융감독원 금융상품통합비교공시) API의 baseList+optionList를 합쳐서 한 행 = "상품 하나의
// 특정 예치기간(save_trm) 옵션"으로 저장한다. 실제 API엔 PDF 명세서가 가정했던 min_amount/link_url
// 필드가 없어서(2026-08-31 실측 확인) 이 두 필드는 만들지 않았다 - "가입하러 가기"는 상품 개별
// 페이지가 아니라 finlife 비교공시 사이트로 연결한다(컨트롤러/프론트에서 처리).
@Entity
@Table(
    name = "cached_financial_products",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_cached_financial_products",
            columnNames = {"fin_co_no", "fin_prdt_cd", "save_trm", "product_type"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CachedFinancialProduct {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "fin_co_no", nullable = false, length = 20)
  private String finCoNo;

  @Column(name = "fin_prdt_cd", nullable = false, length = 30)
  private String finPrdtCd;

  @Setter
  @Column(name = "bank_name", nullable = false, length = 50)
  private String bankName;

  @Setter
  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  @Enumerated(EnumType.STRING)
  @Column(name = "product_type", nullable = false, length = 10)
  private ProductType productType;

  // 예치기간(개월) - finlife optionList의 save_trm 그대로. 목표 기간(FundGoal.targetPeriodMonth)에
  // 맞는 상품을 찾을 때 이 값으로 필터링한다.
  @Column(name = "save_trm", nullable = false)
  private Integer saveTrm;

  @Setter
  @Column(name = "intr_rate")
  private Double intrRate; // 기본금리

  @Setter
  @Column(name = "intr_rate2")
  private Double intrRate2; // 최고우대금리 - 추천 정렬 기준으로 사용

  @Setter
  @Column(name = "synced_at", nullable = false)
  private LocalDateTime syncedAt;
}

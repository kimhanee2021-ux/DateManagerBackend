package org.ict.datemanagerbackend.domain.financialplanner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 한국수출입은행 환율 API 캐시(2026-08-31) - 일일 호출 한도가 1,000회로 넉넉하지 않아 요청마다
// 직접 호출하지 않고 1시간 주기 배치로 갱신한다(명세서 3-3 보완사항). currencyCode는 통화기호
// (예: "JPY(100)"이 아니라 정규화한 "JPY") 기준.
@Entity
@Table(name = "cached_exchange_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CachedExchangeRate {

  @Id
  @Column(name = "currency_code", length = 10)
  private String currencyCode;

  @Setter
  @Column(nullable = false)
  private Double rate;

  @Setter
  @Column(name = "prev_rate")
  private Double prevRate;

  @Setter
  @Column(name = "fetched_at", nullable = false)
  private LocalDateTime fetchedAt;
}

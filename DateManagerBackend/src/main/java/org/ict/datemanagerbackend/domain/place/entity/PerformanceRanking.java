package org.ict.datemanagerbackend.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// KOPIS 박스오피스(예매율 순위) API 결과를 담는 테이블. PlaceAmenity와 같은 이유로 Place와
// 1:1이 아니라 N:1로 둔다 - 모든 공연이 매일 순위에 오르는 게 아니라서(상위 몇십 건만 내려옴)
// Place에 컬럼을 얹는 대신 "그날의 순위표"를 별도 행으로 갖는 편이 자연스럽다.
// 매일 동기화할 때 그 전날 순위를 전부 지우고 새로 채우는 전체교체 방식이라(검색 기반 소스와 동일한
// 이유 - 순위가 매일 바뀌므로 upsert보다 삭제 후 재삽입이 더 단순하고 정확함) rank/장르/지역 전부
// 생성 이후 불변이라 setter가 없다.
@Entity
@Table(name = "performance_rankings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class PerformanceRanking {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "place_id", nullable = false)
  private Place place; // 장소(공연) - places.id 참조. KOPIS mt20id로 기존 Place와 매칭됨

  // RANK는 오라클 예약어(분석 함수)라 컬럼명 충돌을 피하려고 rank_no로 둠
  @Column(name = "rank_no", nullable = false)
  private Integer rankNo; // 박스오피스 순위 (1위부터)

  @Column(name = "genre_name", length = 50)
  private String genreName; // KOPIS 응답의 장르(cate) - 뮤지컬/연극/대중음악 등

  @Column(length = 50)
  private String area; // KOPIS 응답의 지역(area)

}

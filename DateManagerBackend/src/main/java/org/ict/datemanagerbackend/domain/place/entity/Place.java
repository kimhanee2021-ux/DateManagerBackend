package org.ict.datemanagerbackend.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// name/category/address/latitude/longitude/imageUrl은 외부 API(KOPIS 등) 재동기화 시 갱신될 수 있어
// 개별 setter를 열어둔다. externalSource/externalId는 동기화 식별 키라 생성 이후 바뀌지 않는다.
@Entity
@Table(
    name = "places",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_places_external", columnNames = {"external_source", "external_id"})
    },
    // 중복 장소 판단(PlaceDedupService.findDuplicate)이 매 동기화마다 위경도 범위로 findNearby를
    // 호출하는데, 인덱스가 없어서 8만여 건 전체를 스캔해야 했다 - 숙박 CSV 3만 건을 재동기화할 때
    // 이 조회가 3만 번 반복되면서 동기화 하나에 수십 분이 걸리는 문제가 있었다(2026-08-19).
    indexes = {
        @Index(name = "idx_places_lat_lng", columnList = "latitude, longitude")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // Builder 전용, 외부에서 직접 호출 금지
@Builder
public class Place {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id; // 장소 ID (PK)

  @Setter
  // 100자로 뒀다가 KOPIS 공연 제목 중 100자를 넘는 게 있어서(ORA-12899) 255로 넓힘
  @Column(name = "name", nullable = false, length = 255)
  private String name; // 장소명

  @Setter
  @Column(name = "category", length = 50)
  private String category; // 카테고리 (카페, 맛집 등)

  // 세부분류 + 성향점수 공식을 담은 참조 행. 아직 세분류 작업이 안 끝난 장소는 null로 남아있을 수 있다
  // (그런 경우 PlaceStyle의 중립값 50을 그대로 씀). 2026-08-14 신규.
  @Setter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "place_category_id")
  private PlaceCategory placeCategory;

  @Setter
  @Column(name = "address", length = 255)
  private String address; // 도로명 주소

  @Setter
  @Column(name = "latitude")
  private Double latitude; // 위도

  @Setter
  @Column(name = "longitude")
  private Double longitude; // 경도

  @Setter
  @Column(name = "image_url", length = 500)
  private String imageUrl; // 썸네일 URL

  // 주의: places 테이블은 DB 레벨 DEFAULT(SYSTIMESTAMP)가 없다(수동 DDL을 안 쓰고 JPA ddl-auto로만
  // 생성된 테이블이라 Hibernate가 컬럼 기본값까지는 자동으로 만들어주지 않음). 그래서 다른 도메인의
  // insertable=false 패턴과 달리, 여기는 애플리케이션(자바)이 직접 값을 채워야 한다.
  // @Builder.Default: 빌더로 객체를 만들 때 이 필드를 명시적으로 안 채우면 자동으로 LocalDateTime.now()가
  // 들어가도록 함 - 그래서 각 동기화 서비스(PlaceSyncService 등)가 매번 createdAt을 신경 안 써도 됨.
  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now(); // 생성 일시

  @Column(name = "external_source", length = 30)
  private String externalSource; // 데이터 출처 (KOPIS 등 외부 연동 API 식별자)

  @Column(name = "external_id", length = 50)
  private String externalId; // 출처 쪽 고유 ID (예: KOPIS mt20id) - 동기화 시 중복 삽입 방지용

  // ── 아래는 공연류(KOPIS) 전용 필드 - 다른 소스(TourAPI/네이버 등)는 전부 null로 남는다.
  // Place 하나에 다 넣은 이유: 이번 하나만을 위해 별도 1:1 테이블(PlaceReality처럼)을 새로 만들
  // 정도로 크지 않고, external_source로 이미 "이게 KOPIS 데이터냐"를 구분할 수 있어서 굳이 테이블을
  // 쪼갤 실익이 적다고 판단(2026-08-13, 큐레이션 탭 실데이터 전환 준비 작업).

  @Setter
  @Column(name = "start_date")
  private LocalDate startDate; // 공연시작일 (KOPIS prfpdfrom)

  @Setter
  @Column(name = "end_date")
  private LocalDate endDate; // 공연종료일 (KOPIS prfpdto)

  @Setter
  @Column(name = "runtime_text", length = 100)
  private String runtimeText; // 공연시간 (KOPIS prfruntime, 예: "1시간 15분")

  @Setter
  @Column(name = "price_info", length = 200)
  private String priceInfo; // 가격 안내 (KOPIS pcseguidance, 예: "전석 50,000원")

  @Setter
  @Column(name = "show_time_info", length = 300)
  private String showTimeInfo; // 요일별 공연시간 안내 (KOPIS dtguidance, 예: "토요일(14:00,17:00)")

  @Setter
  @Column(name = "booking_url", length = 500)
  private String bookingUrl; // 예매처 링크 (KOPIS relates>relate>relateurl)

  @Setter
  @Column(name = "is_open_run")
  private Integer isOpenRun; // 오픈런 여부 (0/1, KOPIS openrun Y/N)

  @Setter
  @Column(name = "performance_state", length = 20)
  private String performanceState; // 공연상태 (KOPIS prfstate: 공연예정/공연중/공연완료)

}
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
  // 200자로 뒀다가 KOPIS 가격안내 중 등급별 가격이 여러 줄 나열된 게 275자를 넘어서(ORA-12899,
  // 2026-08-27) name 필드와 같은 이유로 500으로 넓힘.
  @Column(name = "price_info", length = 500)
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

  // 공연장(시설) 식별 정보(2026-08-27 추가) - KOPIS 목록조회 응답에 공연장명(fcltynm)이 오고
  // 상세조회로 공연장 ID(mt10id, 좌표 조회에 이미 쓰던 값)도 얻어오는데, 지금까지는 좌표만 뽑아
  // 쓰고 이 둘을 버렸다. 그래서 "이 장소(공연 하나) 말고, 같은 공연장에서 하는 다른 공연들"을
  // 조회할 방법이 DB에 없었다 - "성남아트센터 자체가 아니라 거기서 하는 공연이 중요하다"는 제품
  // 방향(2026-08-27) 때문에 이 그룹핑 키가 필요해짐. venueId(mt10id)로 정확히 그룹핑하고,
  // venueName은 화면에 "○○아트센터에서 하는 다른 공연" 같은 문구를 보여줄 때 쓴다.
  @Setter
  @Column(name = "venue_name", length = 255)
  private String venueName; // 공연장명 (KOPIS fcltynm)

  @Setter
  @Column(name = "venue_id", length = 20)
  private String venueId; // 공연장 ID (KOPIS mt10id) - 같은 공연장의 다른 공연을 찾는 그룹핑 키

  // TourAPI(공공데이터) 좌표가 실제 위치와 수백m씩 어긋나는 사례가 있어(2026-08-20, "홍원" 건 -
  // mapx/mapy 자체가 원본에서부터 틀림) PlaceCoordinateVerificationService가 주소 기반으로 카카오와
  // 대조해 한 번 검증한 장소는 true로 표시한다. TourApiSyncServiceImpl의 매일 새벽 재동기화가
  // 검증 완료된 좌표를 TourAPI 원본 값으로 다시 덮어쓰지 않도록 막는 용도.
  @Setter
  @Column(name = "coordinate_verified")
  private Boolean coordinateVerified;

  // TourAPI detailIntro2/detailImage2 상세정보 동기화(2026-08-26 추가)를 이미 한 번 시도했는지
  // 표시한다. 개발계정 일일 호출 제한(1,000건) 안에서 나눠 돌려야 해서, 매 배치마다 이미 처리한
  // 장소를 건너뛰고 안 한 장소부터 이어가기 위한 커서 역할. 응답이 비어있어도(정보 없음) true로
  // 표시해 같은 장소를 매번 다시 시도하지 않는다.
  @Setter
  @Column(name = "detail_synced")
  private Boolean detailSynced;

  // 폐업 추정 플래그(2026-08-27) - 소상공인 API + 카카오 로컬 검색 둘 다에서 이름 일치 후보를
  // 못 찾은 경우에만 세운다(하나만 없는 건 그 소스의 커버리지 문제일 수 있어 근거로 약함).
  // 바로 삭제하지 않고 표시만 해두는 이유는 Place 엔티티 상단 주석 참고 - 사람이 검토 후 삭제할
  // "의심 목록"이지, 자동 삭제 대상이 아니다.
  @Setter
  @Column(name = "closure_suspected")
  private Boolean closureSuspected;

}
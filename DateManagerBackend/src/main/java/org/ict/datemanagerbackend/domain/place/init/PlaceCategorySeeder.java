package org.ict.datemanagerbackend.domain.place.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// 대화로 확정한 10개 대분류(맛집/공연/숙박/전시/박물관·미술관/관광지/액티비티/쇼핑/축제/스포츠) x
// 세부분류를 이름+이모지+성향점수로 채워두는 시더. 이 테이블은 아직 별도 관리 화면이 없고 지금은 대화로
// 점수를 하나씩 정해서 코드에 반영 -> 재시작하는 방식으로 채우는 중이라, 이 SEEDS 목록이 현재 시점의
// "정답"이다. 그래서 매 실행마다 있으면 갱신/없으면 생성(upsert)한다 - 카테고리별로 점수를 조금씩
// 보완해나갈 예정이라 재실행할 때마다 최신 값이 반영돼야 하기 때문 (2026-08-14).
// 5축 점수를 안 정한 세부분류는 Seed.neutral()로 5축 전부 중립값(50)을 준다.
//
// 점수 원칙: 각 세부분류를 대표하는 축 1~2개만 뚜렷하게 밀고 나머지는 중립(50)에 둔다 - 5축을 전부
// 그럴듯하게 채우면 오히려 억지 데이터가 된다는 판단(2026-08-14 대화). 축 의미:
//   energy(활력): 낮음=차분/정적, 높음=활기참/텐션있음
//   immersion(몰입도): 낮음=가볍게 스치듯, 높음=그 순간에 푹 빠짐
//   vibe(분위기): 낮음=캐주얼함, 높음=고급스러움/로맨틱함
//   aesthetic(비주얼): 낮음=꾸밈없음, 높음=인테리어·플레이팅이 예쁨(사진 찍기 좋음)
//   depth(대화 깊이): 낮음=시끌벅적해서 분위기 위주, 높음=조용히 깊은 대화 나누기 좋음
//
// isIndoor(실내=1/실외=0)/isActivity(0~100, 2026-08-20 신규)는 이 시더가 처음부터 값을 안 채워서
// 지금까지 전부 엔티티 기본값(indoor=1, activity=0)으로만 남아있었다(큐레이션 화면 날씨 자동
// 필터/액티비티 게이지 기능이 아직 없어서 안 쓰였음, project-date-manager-0820 메모리 참고) - 이번에
// 처음으로 실제 값을 매긴다. isActivity는 "에너지가 넘치는 느낌"이 아니라 "몸을 직접 움직여
// 참여하느냐"를 기준으로 잡았다 - 대형 콘서트처럼 에너지 점수가 높아도 앉아서/서서 "보는" 거면
// 낮게, 클라이밍처럼 실제로 몸을 쓰면 높게. 다른 5축과 마찬가지로 1차값이라 나중에 다듬을 수 있다.
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // PlaceCategoryLinker(2번)가 이 시더로 만든 행을 참조하므로 반드시 먼저 실행돼야 함
public class PlaceCategorySeeder implements ApplicationRunner {

  private final PlaceCategoryRepository placeCategoryRepository;

  private record Seed(
      String parent, String sub, String emoji,
      int energy, int immersion, int vibe, int aesthetic, int depth, int pacing,
      int isIndoor, int isActivity
  ) {
    // 즉흥·계획(pacing) 축(2026-08-20 추가) - 기존 9-인자 생성자 호출부를 전부 안 고쳐도 되게
    // pacing=50(중립)을 기본으로 채워주는 보조 생성자. 실제 근거가 생긴 세부분류만 아래 11-인자
    // 생성자로 바꿔서 명시적으로 채운다(우선 맛집부터).
    Seed(String parent, String sub, String emoji,
         int energy, int immersion, int vibe, int aesthetic, int depth,
         int isIndoor, int isActivity) {
      this(parent, sub, emoji, energy, immersion, vibe, aesthetic, depth, 50, isIndoor, isActivity);
    }

    static Seed neutral(String parent, String sub, String emoji, int isIndoor, int isActivity) {
      return new Seed(parent, sub, emoji, 50, 50, 50, 50, 50, isIndoor, isActivity);
    }
  }

  private static final List<Seed> SEEDS = List.of(
      // 맛집 (2026-08-20 성향점수 재검토안 전체 반영 - PERSONA_AXES 기준 재정의). "캐주얼
      // 맛집/브런치"(TourAPI)와 "카페(브런치)"(카카오)는 내용상 사실상 동일해 "브런치"로 통합
      // 결정했으나, DB의 place_category_id 재배정(구조 변경)은 별도 작업으로 미루고 지금은 두 행 모두
      // 같은 최종값을 채워둔다 - 실제 병합/삭제는 후속 마이그레이션에서 처리.
      new Seed("맛집", "조용한 다이닝(파인다이닝/오마카세)", "🍽️", 50, 50, 70, 50, 50, 80, 1, 15),
      new Seed("맛집", "캐주얼 맛집/브런치", "🥐", 50, 50, 60, 50, 50, 55, 1, 20),
      new Seed("맛집", "술집/포차/이자카야", "🍻", 55, 50, 50, 50, 50, 60, 1, 20),
      new Seed("맛집", "로컬 노포/전통시장", "🍢", 50, 50, 5, 50, 50, 35, 0, 40),

      // 맛집 - 카카오 API의 category_name(음식점 > 한식 > ...)으로 세분화(2026-08-18). 위 4개는
      // "경험 유형" 기준이고 이 아래는 "음식 종류" 기준이라 서로 다른 축이지만, PlaceCategory가
      // place당 1개만 붙는 구조라 카카오 소스는 이 음식종류 기준으로만 연결한다(KakaoPlaceSyncService).
      // 웨이팅 등 판단 근거가 없는 8개 식당 종류는 재검토에서도 전부 중립(50) 유지가 확정값 - "안 정한
      // 게 아니라 안 쓰기로 확정한" 상태다.
      Seed.neutral("맛집", "식당(한식)", "🍚", 1, 15),
      Seed.neutral("맛집", "식당(중식)", "🥟", 1, 15),
      Seed.neutral("맛집", "식당(일식)", "🍣", 1, 15),
      Seed.neutral("맛집", "식당(양식)", "🍝", 1, 15),
      Seed.neutral("맛집", "식당(아시아음식)", "🍜", 1, 15),
      Seed.neutral("맛집", "식당(분식)", "🌭", 1, 20),
      Seed.neutral("맛집", "식당(뷔페)", "🍱", 1, 25),
      Seed.neutral("맛집", "식당(기타)", "🍴", 1, 15),
      new Seed("맛집", "카페(브런치)", "🥐", 50, 50, 60, 50, 50, 55, 1, 10),
      new Seed("맛집", "카페(일반)", "☕", 50, 50, 65, 50, 50, 60, 1, 10),
      // 베이커리/제과점(2026-08-31 신규) - 소상공인 매칭 실패 패턴 1위("기타 간이 빵/도넛 제과점업")
      // 대응. 카페(일반)과 비슷하되 비주얼(사진 찍기 좋음)만 조금 더 높고 나머지는 그대로 - 사용자 확정.
      new Seed("맛집", "베이커리/제과점", "🥖", 50, 50, 55, 60, 50, 45, 1, 10),

      // 공연 (2026-08-20 성향점수 재검토안 전체 반영). "복합공연"은 여러 장르가 섞인 소규모·커뮤니티성
      // 공연이라 6축 전부 중립 확정.
      Seed.neutral("공연", "복합공연", "🎟️", 1, 20),
      new Seed("공연", "서커스/마술", "🎩", 50, 30, 50, 65, 35, 50, 1, 20),

      new Seed("공연", "클래식/오페라", "🎻", 25, 10, 50, 50, 50, 50, 1, 10),
      new Seed("공연", "뮤지컬", "🎹", 50, 25, 65, 50, 45, 60, 1, 15),
      new Seed("공연", "소극장/어쿠스틱 콘서트(발라드류)", "🎸", 25, 20, 55, 40, 65, 50, 1, 15),
      new Seed("공연", "대형 콘서트(아이돌/스타디움)", "🎤", 90, 60, 50, 50, 50, 70, 0, 55),
      new Seed("공연", "이머시브 연극", "🎪", 65, 95, 50, 65, 55, 50, 1, 75),
      new Seed("공연", "관람형 연극", "🎭", 35, 15, 50, 20, 50, 50, 1, 10),
      new Seed("공연", "무용/발레", "🩰", 40, 15, 50, 50, 65, 50, 1, 10),
      new Seed("공연", "국악/전통공연", "🥁", 30, 25, 50, 50, 50, 50, 1, 15),
      new Seed("공연", "뮤직페스티벌", "🎶", 95, 85, 50, 50, 20, 65, 0, 65),

      // 숙박 (2026-08-20 성향점수 재검토안 전체 반영) - V(로컬↔프리미엄)·P(즉흥↔계획) 두 축만
      // 의미 있다고 판단해 나머지(E·I·A·D)는 전부 중립 고정.
      new Seed("숙박", "호텔", "🏨", 50, 50, 80, 50, 50, 70, 1, 15),
      new Seed("숙박", "리조트", "🏝️", 50, 50, 90, 50, 50, 65, 1, 35),
      new Seed("숙박", "펜션", "🏡", 50, 50, 60, 50, 50, 60, 1, 25),
      new Seed("숙박", "한옥", "🏯", 50, 50, 15, 50, 50, 60, 1, 20),
      new Seed("숙박", "게스트하우스", "🛏️", 50, 50, 35, 50, 50, 35, 1, 25),
      new Seed("숙박", "모텔", "🛌", 50, 50, 30, 50, 50, 30, 1, 10),

      // 전시 (2026-08-20 성향점수 재검토안 전체 반영, 2026-08-28 "도슨트 전시" -> "역사·이야기 전시"로
      // 용도 재정의 - 사용자 요청: "도슨트"라는 이름 자체가 붙는 곳은 거의 없어서(실측 0건) 키워드
      // 매칭 대상이 아니라, 대신 해설·서사가 중요한 콘텐츠(역사관/화랑/과학관 등)를 여기로 모은다.
      // 점수(미감 최저·깊이 최고)는 원래 의도 그대로 유지 - "보기 좋음"보다 "이야기"가 중요한 축.
      new Seed("전시", "역사·이야기 전시", "📜", 25, 25, 50, 15, 75, 50, 1, 35),
      new Seed("전시", "일반 전시", "🖼️", 25, 20, 50, 55, 55, 50, 1, 30),

      // "문화시설" 미분류 3,715건 중 이름 패턴이 뚜렷한 3종 추가(2026-08-28, 사용자가 직접 축 값을
      // 정함) - 영화관: 대중·작품성(depth)35=다소 대중적, 관람·참여(immersion)30=관람형 쪽,
      // 나머지 축은 근거 없어 중립. 도서관: 고요·축제(energy)25=잔잔함, 로컬·핫플(vibe)35=로컬 쪽.
      // 문화원: 지역 커뮤니티 프로그램 성격이라 "일반 전시"와 동일 점수 사용(사용자 확정).
      new Seed("전시", "영화관", "🎬", 50, 30, 50, 50, 35, 1, 10),
      new Seed("전시", "도서관", "📚", 25, 50, 35, 50, 50, 1, 5),
      new Seed("전시", "문화원", "🏢", 25, 20, 50, 55, 55, 1, 30),

      // 박물관·미술관 (2026-08-20 성향점수 재검토안 전체 반영) - 어린이체험관은 커플 데이트 앱
      // 취지와 안 맞아 제외.
      new Seed("박물관·미술관", "상설미술관", "🎨", 50, 25, 50, 35, 65, 50, 1, 25),
      new Seed("박물관·미술관", "역사/과학박물관", "🏺", 50, 30, 50, 15, 75, 50, 1, 35),
      new Seed("박물관·미술관", "사진/미디어아트관", "📸", 50, 50, 50, 90, 35, 50, 1, 30),

      // 관광지 (2026-08-20 성향점수 재검토안 전체 반영) - A·D는 원칙적으로 중립(쓰지 않는 데이터),
      // 근거가 명확한 축만 예외. "랜드마크"는 실측 데이터가 "전망/야경"과 사실상 겹쳐 통합하기로
      // 했으나(구조 변경은 후속 작업), 지금은 같은 최종값을 채워 둔다.
      new Seed("관광지", "전망/야경", "🌆", 50, 15, 75, 90, 50, 50, 0, 25),
      new Seed("관광지", "역사/유적", "🏛️", 50, 25, 20, 25, 50, 50, 0, 35),
      new Seed("관광지", "자연/경관", "🏞️", 50, 30, 50, 95, 50, 50, 0, 50),
      new Seed("관광지", "골목/동네산책", "🚶", 40, 50, 15, 50, 50, 50, 0, 45),
      new Seed("관광지", "테마파크", "🎢", 95, 90, 65, 50, 50, 50, 0, 85),
      new Seed("관광지", "랜드마크", "🗼", 50, 15, 75, 90, 50, 50, 0, 25),
      // 온천/스파(2026-08-28 신규) - 로컬·핫플=30, 나머지 축은 중립(사용자 지정).
      new Seed("관광지", "온천/스파", "♨️", 50, 50, 30, 50, 50, 0, 35),

      // 액티비티 (2026-08-20 성향점수 재검토안 전체 반영)
      new Seed("액티비티", "방탈출/미스터리룸", "🔑", 50, 85, 50, 50, 50, 50, 1, 75),
      new Seed("액티비티", "VR/실감형체험", "🥽", 70, 75, 50, 70, 50, 50, 1, 75),
      new Seed("액티비티", "클라이밍/스포츠체험", "🧗", 85, 85, 50, 50, 50, 50, 1, 90),
      new Seed("액티비티", "볼링/당구/오락실", "🎳", 60, 70, 30, 50, 50, 30, 1, 70),
      // 보드게임카페(2026-08-28 신규) - NAVER 지역검색이 카테고리 태그를 "오락실"류로 뭉뚱그려
      // 내려줘서 볼링/당구/오락실과 섞여있던 171건을 분리. 축값은 기본 추정치(사용자 확인 필요) -
      // 신체활동은 낮고 대화 중심 참여도가 높다고 보고 잡음.
      new Seed("액티비티", "보드게임카페", "🎲", 40, 75, 45, 50, 50, 40, 1, 50),
      new Seed("액티비티", "수상레포츠", "🏄", 90, 85, 65, 50, 50, 55, 0, 90),
      new Seed("액티비티", "캠핑/글램핑", "🏕️", 30, 55, 40, 50, 50, 70, 0, 45),
      new Seed("액티비티", "원데이클래스", "🍳", 50, 75, 40, 50, 50, 50, 1, 55),

      // 2026-08-28 사용자 지정 축 점수(관람·참여=immersion, 로컬·핫플=vibe, 고요·축제=energy 등) -
      // 언급 안 된 축은 전부 중립(50) 유지. isIndoor/isActivity는 근거 기반 1차 추정치.
      new Seed("액티비티", "골프", "⛳", 50, 75, 50, 50, 50, 50, 0, 40),
      new Seed("액티비티", "트레킹/산책길", "🥾", 50, 80, 30, 50, 50, 50, 0, 45),
      new Seed("액티비티", "낚시", "🎣", 35, 60, 25, 50, 50, 50, 0, 30),
      new Seed("액티비티", "스키/보드", "⛷️", 50, 75, 65, 50, 50, 50, 0, 70),
      new Seed("액티비티", "승마", "🐎", 50, 65, 50, 50, 50, 50, 0, 40),

      // 쇼핑 (2026-08-20 성향점수 재검토안 전체 반영) - "소품/굿즈샵"은 연결된 실 데이터가 0건이라
      // 전부 중립 확정.
      new Seed("쇼핑", "전통시장", "🏮", 65, 50, 5, 50, 50, 30, 0, 45),
      new Seed("쇼핑", "야시장", "🌙", 70, 50, 10, 50, 50, 35, 0, 50),
      Seed.neutral("쇼핑", "소품/굿즈샵", "🕯️", 1, 20),
      new Seed("쇼핑", "편집숍/빈티지샵", "👗", 50, 50, 65, 50, 50, 50, 1, 20),
      new Seed("쇼핑", "백화점", "🏬", 55, 50, 90, 50, 50, 50, 1, 25),
      new Seed("쇼핑", "아울렛", "🏷️", 60, 50, 45, 50, 50, 60, 0, 35),
      new Seed("쇼핑", "플리마켓/팝업", "🧺", 60, 50, 85, 50, 50, 65, 0, 40),
      // 브랜드 매장(2026-08-28 사용자 지정) - 성수/한남/청담/가로수길/도산 등 플래그십스토어 상권.
      // "로컬·핫플" 기준을 팝업스토어와 동일하게 잡으라는 요청이라 vibe=85로 "플리마켓/팝업"과 맞춤.
      new Seed("쇼핑", "브랜드 매장", "🛍️", 50, 50, 85, 60, 50, 50, 1, 20),
      // 복합쇼핑몰(2026-08-28) - 두타몰/아이파크몰/롯데월드 쇼핑몰처럼 개별 브랜드 지점이 아니라
      // 몰 자체를 가리키는 항목들. 백화점(vibe 90)과 아울렛(vibe 45) 사이의 대중적 쇼핑몰로 보고
      // 기본 추정치를 채움 - 실측 데이터가 쌓이면 조정 필요.
      new Seed("쇼핑", "복합쇼핑몰", "🏢", 55, 50, 55, 50, 45, 50, 1, 30),

      // 축제 (2026-08-20 성향점수 재검토안 전체 반영) - 4개 세부분류 전부 연결된 실 데이터가
      // 0건이라(place_category_id 매칭 자체가 안 됨) 전부 중립 확정.
      // 축제(2026-08-28 재검토) - "공연" 카테고리 미분류 937건을 분석해보니 대부분 지역축제/
      // 박람회성 데이터였고(공연 세부장르 문제가 아니었음), 그 참에 4개 기존 세부분류도 실측값으로
      // 채우고 3개 신규 세부분류(전통제례/헤리티지나이트, 테마페스티벌, 지역축제)를 추가함.
      new Seed("축제", "계절축제", "🌸", 50, 40, 50, 70, 50, 50, 0, 40),
      new Seed("축제", "불꽃/야간축제", "🎆", 70, 40, 65, 50, 60, 70, 0, 25),
      new Seed("축제", "음식축제", "🥘", 55, 60, 35, 50, 50, 50, 0, 40),
      new Seed("축제", "문화예술축제", "🎬", 55, 40, 50, 50, 50, 50, 0, 35),
      // 전통제례/헤리티지나이트(신규) - 국가유산야행, 궁궐 전통의식류. 로컬·핫플은 낮고
      // 대중·작품성(서사성)은 높게.
      new Seed("축제", "전통제례/헤리티지나이트", "🏯", 50, 50, 10, 50, 70, 50, 0, 30),
      // 테마페스티벌(신규) - 강남/e스포츠/디자인/콘텐츠 등 도시형 브랜드 페스티벌. 공연>뮤직페스티벌과
      // 이름이 겹치지 않도록 구분. 참여형(70), 즉흥보다 계획(55) 쪽.
      new Seed("축제", "테마페스티벌", "🎪", 50, 70, 50, 50, 50, 55, 0, 35),
      // 지역축제(신규) - 지자체 전통/특산물 축제 대부분. 로컬 성격이 강해 로컬·핫플을 최저 수준으로.
      new Seed("축제", "지역축제", "🎏", 55, 55, 5, 50, 50, 50, 0, 40),

      // 스포츠 (2026-08-20 성향점수 재검토안 전체 반영, 국내 프로스포츠 직관 - SportsSyncService).
      // 야구는 요즘 인기로 P(계획형) 최상위, 나머지는 즉흥으로 잡음. 대부분 관람 위주라 I는 낮게,
      // A·D는 판단 근거 없어 중립.
      new Seed("스포츠", "야구 직관", "⚾", 85, 35, 45, 50, 50, 90, 0, 40),
      new Seed("스포츠", "축구 직관", "⚽", 90, 35, 30, 50, 50, 35, 0, 40),
      new Seed("스포츠", "농구 직관", "🏀", 75, 35, 30, 50, 50, 35, 1, 40),
      new Seed("스포츠", "배구 직관", "🏐", 75, 35, 30, 50, 50, 35, 1, 40)
  );

  // SEEDS에서 뺀 뒤 DB에 고아 행으로 남아있는 것들을 정리하기 위한 목록.
  // - 공연/콘서트·대중음악: 소극장/대형 콘서트로 분리하면서 남은 옛 통합 항목 (2026-08-14)
  // - 박물관·미술관/어린이체험관: 커플 데이트 앱 취지와 안 맞아 제외 (2026-08-14)
  private static final List<String[]> REMOVED = List.of(
      new String[]{"공연", "콘서트/대중음악"},
      new String[]{"박물관·미술관", "어린이체험관"},
      new String[]{"전시", "도슨트 전시"} // "역사·이야기 전시"로 이름·용도 변경(2026-08-28)
  );

  @Override
  public void run(ApplicationArguments args) {
    int deleted = 0;
    for (String[] removed : REMOVED) {
      var found = placeCategoryRepository.findByParentCategoryAndSubCategory(removed[0], removed[1]);
      if (found.isPresent()) {
        placeCategoryRepository.delete(found.get());
        deleted++;
      }
    }

    int created = 0;
    int updated = 0;
    for (Seed seed : SEEDS) {
      PlaceCategory category = placeCategoryRepository
          .findByParentCategoryAndSubCategory(seed.parent(), seed.sub())
          .orElse(null);

      if (category == null) {
        placeCategoryRepository.save(
            PlaceCategory.builder()
                .parentCategory(seed.parent())
                .subCategory(seed.sub())
                .emoji(seed.emoji())
                .scoreEnergy(seed.energy())
                .scoreImmersion(seed.immersion())
                .scoreVibe(seed.vibe())
                .scoreAesthetic(seed.aesthetic())
                .scoreDepth(seed.depth())
                .scorePacing(seed.pacing())
                .isIndoor(seed.isIndoor())
                .isActivity(seed.isActivity())
                .build()
        );
        created++;
        continue;
      }

      category.setEmoji(seed.emoji());
      category.setScoreEnergy(seed.energy());
      category.setScoreImmersion(seed.immersion());
      category.setScoreVibe(seed.vibe());
      category.setScoreAesthetic(seed.aesthetic());
      category.setScoreDepth(seed.depth());
      category.setScorePacing(seed.pacing());
      category.setIsIndoor(seed.isIndoor());
      category.setIsActivity(seed.isActivity());
      placeCategoryRepository.save(category);
      updated++;
    }
    log.info("PlaceCategory 시드 반영 완료 (생성 {}건, 갱신 {}건, 정리 {}건)", created, updated, deleted);
  }
}

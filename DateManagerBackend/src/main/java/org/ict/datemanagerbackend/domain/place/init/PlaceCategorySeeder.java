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
      int energy, int immersion, int vibe, int aesthetic, int depth,
      int isIndoor, int isActivity
  ) {
    static Seed neutral(String parent, String sub, String emoji, int isIndoor, int isActivity) {
      return new Seed(parent, sub, emoji, 50, 50, 50, 50, 50, isIndoor, isActivity);
    }
  }

  private static final List<Seed> SEEDS = List.of(
      // 맛집 (2026-08-14 5축 점수 확정, 2026-08-20 indoor/activity 추가 - 전부 실내에서 앉아 먹는
      // 형태라 activity는 낮게, 노포/전통시장만 돌아다니며 먹는 느낌이라 실외+약간 상향)
      new Seed("맛집", "조용한 다이닝(파인다이닝/오마카세)", "🍽️", 50, 50, 85, 50, 75, 1, 15),
      new Seed("맛집", "캐주얼 맛집/브런치", "🥐", 50, 50, 50, 70, 50, 1, 20),
      new Seed("맛집", "술집/포차/이자카야", "🍻", 75, 50, 50, 50, 65, 1, 20),
      new Seed("맛집", "로컬 노포/전통시장", "🍢", 65, 50, 35, 50, 50, 0, 40),

      // 맛집 - 카카오 API의 category_name(음식점 > 한식 > ...)으로 세분화(2026-08-18). 위 4개는
      // "경험 유형" 기준이고 이 아래는 "음식 종류" 기준이라 서로 다른 축이지만, PlaceCategory가
      // place당 1개만 붙는 구조라 카카오 소스는 이 음식종류 기준으로만 연결한다(KakaoPlaceSyncService).
      // 5축 점수는 아직 대화로 확정 안 해서 전부 중립(50) - 나중에 채워 넣을 것. indoor/activity는
      // 식당류라 이미 명확해서 같이 채움(뷔페만 돌아다니는 형태라 약간 상향).
      Seed.neutral("맛집", "식당(한식)", "🍚", 1, 15),
      Seed.neutral("맛집", "식당(중식)", "🥟", 1, 15),
      Seed.neutral("맛집", "식당(일식)", "🍣", 1, 15),
      Seed.neutral("맛집", "식당(양식)", "🍝", 1, 15),
      Seed.neutral("맛집", "식당(아시아음식)", "🍜", 1, 15),
      Seed.neutral("맛집", "식당(분식)", "🌭", 1, 20),
      Seed.neutral("맛집", "식당(뷔페)", "🍱", 1, 25),
      Seed.neutral("맛집", "식당(기타)", "🍴", 1, 15),
      Seed.neutral("맛집", "카페(브런치)", "🥐", 1, 10),
      Seed.neutral("맛집", "카페(일반)", "☕", 1, 10),

      // 공연 - KOPIS 박스오피스 장르(cate) 중 기존 9개 세부분류 어디에도 안 맞는 것들(2026-08-19).
      // 5축 점수는 아직 확정 안 해서 중립(50) - 나중에 채워 넣을 것. 관람형 실내 공연이라 activity 낮음.
      Seed.neutral("공연", "서커스/마술", "🎩", 1, 20),
      Seed.neutral("공연", "복합공연", "🎟️", 1, 20),

      // 공연 (2026-08-14 5축 점수 확정 - 콘서트/대중음악은 소극장형/대형으로 성향이 완전 갈려서 둘로
      // 분리). 2026-08-20 indoor/activity 추가 - 대형 콘서트/뮤직페스티벌은 흔히 야외 스타디움이라
      // 실외로 두고, 서서 뛰며 응원하는 문화가 있어 activity를 다른 관람형 공연보다 높게 잡음.
      // 이머시브 연극은 관객이 배우와 함께 움직이며 참여하는 형태라 activity를 가장 높게 잡음.
      new Seed("공연", "클래식/오페라", "🎻", 50, 65, 85, 50, 50, 1, 10),
      new Seed("공연", "뮤지컬", "🎹", 50, 80, 50, 60, 50, 1, 15),
      new Seed("공연", "소극장/어쿠스틱 콘서트(발라드류)", "🎸", 30, 50, 50, 50, 70, 1, 15),
      new Seed("공연", "대형 콘서트(아이돌/스타디움)", "🎤", 85, 50, 50, 65, 50, 0, 55),
      new Seed("공연", "이머시브 연극", "🎪", 50, 90, 50, 50, 50, 1, 75),
      new Seed("공연", "관람형 연극", "🎭", 50, 35, 50, 50, 60, 1, 10),
      new Seed("공연", "무용/발레", "🩰", 50, 50, 65, 85, 50, 1, 10),
      new Seed("공연", "국악/전통공연", "🥁", 35, 50, 50, 60, 50, 1, 15),
      new Seed("공연", "뮤직페스티벌", "🎶", 90, 50, 30, 50, 50, 0, 65),

      // 숙박 (2026-08-14 5축 점수 확정 - 게스트하우스/모텔 추가). 2026-08-20 indoor/activity 추가 -
      // 전부 실내 시설이고, 리조트만 수영장 등 부대시설 활동 가능성으로 약간 상향.
      new Seed("숙박", "호텔", "🏨", 50, 50, 65, 50, 50, 1, 15),
      new Seed("숙박", "리조트", "🏝️", 50, 50, 70, 75, 50, 1, 35),
      new Seed("숙박", "펜션", "🏡", 50, 50, 35, 50, 75, 1, 25),
      new Seed("숙박", "한옥", "🏯", 30, 50, 50, 80, 50, 1, 20),
      new Seed("숙박", "게스트하우스", "🛏️", 60, 50, 20, 50, 50, 1, 25),
      new Seed("숙박", "모텔", "🛌", 40, 50, 25, 50, 50, 1, 10),

      // 전시 (2026-08-14 5축 점수 확정, 2026-08-20 indoor/activity 추가 - 실내, 걸어다니며 관람하는
      // 정도라 다른 관람형보다는 조금 높게)
      new Seed("전시", "도슨트 전시", "🎧", 50, 75, 50, 50, 60, 1, 35),
      new Seed("전시", "일반 전시", "🖼️", 50, 35, 50, 60, 50, 1, 30),

      // 박물관·미술관 (2026-08-14 5축 점수 확정) - 어린이체험관은 커플 데이트 앱 취지와 안 맞아 제외.
      // 2026-08-20 indoor/activity 추가 - 실내, 역사/과학박물관은 체험 전시가 섞여있어 약간 상향.
      new Seed("박물관·미술관", "상설미술관", "🎨", 50, 50, 50, 75, 50, 1, 25),
      new Seed("박물관·미술관", "역사/과학박물관", "🏺", 50, 65, 50, 50, 50, 1, 35),
      new Seed("박물관·미술관", "사진/미디어아트관", "📸", 55, 50, 50, 85, 50, 1, 30),

      // 관광지 (2026-08-14 5축 점수 확정, 2026-08-20 indoor/activity 추가 - 전부 실외. 테마파크는
      // 놀이기구 등으로 activity 가장 높게, 전망/랜드마크는 서서 보는 정도라 낮게)
      new Seed("관광지", "전망/야경", "🌆", 50, 50, 70, 65, 50, 0, 25),
      new Seed("관광지", "역사/유적", "🏛️", 35, 50, 50, 50, 60, 0, 35),
      new Seed("관광지", "자연/경관", "🏞️", 65, 50, 50, 70, 50, 0, 50),
      new Seed("관광지", "골목/동네산책", "🚶", 55, 50, 40, 50, 50, 0, 45),
      new Seed("관광지", "테마파크", "🎢", 85, 50, 30, 50, 50, 0, 85),
      new Seed("관광지", "랜드마크", "🗼", 40, 50, 50, 65, 50, 0, 25),

      // 액티비티 (2026-08-14 5축 점수 확정, 2026-08-20 indoor/activity 추가 - 전부 몸을 직접 쓰는
      // 곳이라 activity를 높게 잡음. 수상레포츠/캠핑은 실외, 나머지는 실내 시설 기준)
      new Seed("액티비티", "방탈출/미스터리룸", "🔑", 50, 80, 50, 50, 55, 1, 75),
      new Seed("액티비티", "VR/실감형체험", "🥽", 65, 65, 50, 50, 50, 1, 75),
      new Seed("액티비티", "클라이밍/스포츠체험", "🧗", 85, 50, 50, 50, 50, 1, 90),
      new Seed("액티비티", "볼링/당구/오락실", "🎳", 55, 50, 30, 50, 50, 1, 70),
      new Seed("액티비티", "수상레포츠", "🏄", 90, 50, 50, 50, 50, 0, 90),
      new Seed("액티비티", "캠핑/글램핑", "🏕️", 40, 50, 50, 50, 70, 0, 45),
      new Seed("액티비티", "원데이클래스", "🍳", 50, 60, 50, 55, 50, 1, 55),

      // 쇼핑 (2026-08-14 5축 점수 확정, 2026-08-20 indoor/activity 추가 - 시장/야시장/아울렛/플리마켓은
      // 야외형이 많아 실외로, 백화점/편집숍/굿즈샵은 실내로 잡음)
      new Seed("쇼핑", "전통시장", "🏮", 65, 50, 35, 50, 50, 0, 45),
      new Seed("쇼핑", "야시장", "🌙", 70, 50, 50, 55, 50, 0, 50),
      new Seed("쇼핑", "소품/굿즈샵", "🕯️", 35, 50, 50, 70, 50, 1, 20),
      new Seed("쇼핑", "편집숍/빈티지샵", "👗", 50, 50, 60, 60, 50, 1, 20),
      new Seed("쇼핑", "백화점", "🏬", 50, 50, 65, 50, 50, 1, 25),
      new Seed("쇼핑", "아울렛", "🏷️", 50, 50, 30, 50, 50, 0, 35),
      new Seed("쇼핑", "플리마켓/팝업", "🧺", 60, 50, 50, 55, 50, 0, 40),

      // 축제 (2026-08-14 5축 점수 확정, 2026-08-20 indoor/activity 추가 - 축제는 기본적으로 야외
      // 행사라 전부 실외로 잡음)
      new Seed("축제", "계절축제", "🌸", 50, 50, 55, 70, 50, 0, 40),
      new Seed("축제", "불꽃/야간축제", "🎆", 75, 50, 60, 50, 50, 0, 25),
      new Seed("축제", "음식축제", "🥘", 60, 50, 30, 50, 50, 0, 40),
      new Seed("축제", "문화예술축제", "🎬", 50, 60, 50, 55, 50, 0, 35),

      // 스포츠 (2026-08-20 신규, 국내 프로스포츠 직관 - SportsSyncService) - "대형 콘서트" 점수를
      // 참고 삼아 잡은 1차 값이라, 다른 카테고리처럼 나중에 대화로 다듬을 여지가 있음. 공통적으로
      // 활력·몰입은 높고(함성·응원), 대화 나누기는 어려움(depth 낮음)으로 잡았다. 야구/축구는 흔히
      // 야외 구장이라 실외, 농구/배구는 실내 체육관이 일반적이라 실내로 뒀다 - 관람이지만 응원 문화로
      // 서 있는 시간이 많아 activity는 순수 관람형 공연보다 높게 잡음.
      new Seed("스포츠", "야구 직관", "⚾", 80, 70, 50, 50, 35, 0, 40),
      new Seed("스포츠", "축구 직관", "⚽", 85, 75, 50, 50, 30, 0, 40),
      new Seed("스포츠", "농구 직관", "🏀", 80, 70, 50, 50, 35, 1, 40),
      new Seed("스포츠", "배구 직관", "🏐", 75, 65, 50, 50, 40, 1, 40)
  );

  // SEEDS에서 뺀 뒤 DB에 고아 행으로 남아있는 것들을 정리하기 위한 목록.
  // - 공연/콘서트·대중음악: 소극장/대형 콘서트로 분리하면서 남은 옛 통합 항목 (2026-08-14)
  // - 박물관·미술관/어린이체험관: 커플 데이트 앱 취지와 안 맞아 제외 (2026-08-14)
  private static final List<String[]> REMOVED = List.of(
      new String[]{"공연", "콘서트/대중음악"},
      new String[]{"박물관·미술관", "어린이체험관"}
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
      category.setIsIndoor(seed.isIndoor());
      category.setIsActivity(seed.isActivity());
      placeCategoryRepository.save(category);
      updated++;
    }
    log.info("PlaceCategory 시드 반영 완료 (생성 {}건, 갱신 {}건, 정리 {}건)", created, updated, deleted);
  }
}

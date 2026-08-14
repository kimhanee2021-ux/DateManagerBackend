package org.ict.datemanagerbackend.domain.place.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

// 대화로 확정한 9개 대분류(맛집/공연/숙박/전시/박물관·미술관/관광지/액티비티/쇼핑/축제) x 46개 세부분류를
// 이름+이모지+성향점수로 채워두는 시더. 이 테이블은 아직 별도 관리 화면이 없고 지금은 대화로 점수를
// 하나씩 정해서 코드에 반영 -> 재시작하는 방식으로 채우는 중이라, 이 SEEDS 목록이 현재 시점의
// "정답"이다. 그래서 매 실행마다 있으면 갱신/없으면 생성(upsert)한다 - 카테고리별로 점수를 조금씩
// 보완해나갈 예정이라 재실행할 때마다 최신 값이 반영돼야 하기 때문 (2026-08-14).
// 점수를 안 정한 세부분류는 Seed.neutral()로 5축 전부 중립값(50)을 준다.
//
// 점수 원칙: 각 세부분류를 대표하는 축 1~2개만 뚜렷하게 밀고 나머지는 중립(50)에 둔다 - 5축을 전부
// 그럴듯하게 채우면 오히려 억지 데이터가 된다는 판단(2026-08-14 대화). 축 의미:
//   energy(활력): 낮음=차분/정적, 높음=활기참/텐션있음
//   immersion(몰입도): 낮음=가볍게 스치듯, 높음=그 순간에 푹 빠짐
//   vibe(분위기): 낮음=캐주얼함, 높음=고급스러움/로맨틱함
//   aesthetic(비주얼): 낮음=꾸밈없음, 높음=인테리어·플레이팅이 예쁨(사진 찍기 좋음)
//   depth(대화 깊이): 낮음=시끌벅적해서 분위기 위주, 높음=조용히 깊은 대화 나누기 좋음
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // PlaceCategoryLinker(2번)가 이 시더로 만든 행을 참조하므로 반드시 먼저 실행돼야 함
public class PlaceCategorySeeder implements ApplicationRunner {

  private final PlaceCategoryRepository placeCategoryRepository;

  private record Seed(
      String parent, String sub, String emoji,
      int energy, int immersion, int vibe, int aesthetic, int depth
  ) {
    static Seed neutral(String parent, String sub, String emoji) {
      return new Seed(parent, sub, emoji, 50, 50, 50, 50, 50);
    }
  }

  private static final List<Seed> SEEDS = List.of(
      // 맛집 (2026-08-14 점수 확정)
      new Seed("맛집", "조용한 다이닝(파인다이닝/오마카세)", "🍽️", 50, 50, 85, 50, 75),
      new Seed("맛집", "캐주얼 맛집/브런치", "🥐", 50, 50, 50, 70, 50),
      new Seed("맛집", "술집/포차/이자카야", "🍻", 75, 50, 50, 50, 65),
      new Seed("맛집", "로컬 노포/전통시장", "🍢", 65, 50, 35, 50, 50),

      // 공연 (2026-08-14 점수 확정 - 콘서트/대중음악은 소극장형/대형으로 성향이 완전 갈려서 둘로 분리)
      new Seed("공연", "클래식/오페라", "🎻", 50, 65, 85, 50, 50),
      new Seed("공연", "뮤지컬", "🎹", 50, 80, 50, 60, 50),
      new Seed("공연", "소극장/어쿠스틱 콘서트(발라드류)", "🎸", 30, 50, 50, 50, 70),
      new Seed("공연", "대형 콘서트(아이돌/스타디움)", "🎤", 85, 50, 50, 65, 50),
      new Seed("공연", "이머시브 연극", "🎪", 50, 90, 50, 50, 50),
      new Seed("공연", "관람형 연극", "🎭", 50, 35, 50, 50, 60),
      new Seed("공연", "무용/발레", "🩰", 50, 50, 65, 85, 50),
      new Seed("공연", "국악/전통공연", "🥁", 35, 50, 50, 60, 50),
      new Seed("공연", "뮤직페스티벌", "🎶", 90, 50, 30, 50, 50),

      // 숙박 (2026-08-14 점수 확정 - 게스트하우스/모텔 추가)
      new Seed("숙박", "호텔", "🏨", 50, 50, 65, 50, 50),
      new Seed("숙박", "리조트", "🏝️", 50, 50, 70, 75, 50),
      new Seed("숙박", "펜션", "🏡", 50, 50, 35, 50, 75),
      new Seed("숙박", "한옥", "🏯", 30, 50, 50, 80, 50),
      new Seed("숙박", "게스트하우스", "🛏️", 60, 50, 20, 50, 50),
      new Seed("숙박", "모텔", "🛌", 40, 50, 25, 50, 50),

      // 전시 (2026-08-14 점수 확정)
      new Seed("전시", "도슨트 전시", "🎧", 50, 75, 50, 50, 60),
      new Seed("전시", "일반 전시", "🖼️", 50, 35, 50, 60, 50),

      // 박물관·미술관 (2026-08-14 점수 확정) - 어린이체험관은 커플 데이트 앱 취지와 안 맞아 제외
      new Seed("박물관·미술관", "상설미술관", "🎨", 50, 50, 50, 75, 50),
      new Seed("박물관·미술관", "역사/과학박물관", "🏺", 50, 65, 50, 50, 50),
      new Seed("박물관·미술관", "사진/미디어아트관", "📸", 55, 50, 50, 85, 50),

      // 관광지 (2026-08-14 점수 확정)
      new Seed("관광지", "전망/야경", "🌆", 50, 50, 70, 65, 50),
      new Seed("관광지", "역사/유적", "🏛️", 35, 50, 50, 50, 60),
      new Seed("관광지", "자연/경관", "🏞️", 65, 50, 50, 70, 50),
      new Seed("관광지", "골목/동네산책", "🚶", 55, 50, 40, 50, 50),
      new Seed("관광지", "테마파크", "🎢", 85, 50, 30, 50, 50),
      new Seed("관광지", "랜드마크", "🗼", 40, 50, 50, 65, 50),

      // 액티비티 (2026-08-14 점수 확정)
      new Seed("액티비티", "방탈출/미스터리룸", "🔑", 50, 80, 50, 50, 55),
      new Seed("액티비티", "VR/실감형체험", "🥽", 65, 65, 50, 50, 50),
      new Seed("액티비티", "클라이밍/스포츠체험", "🧗", 85, 50, 50, 50, 50),
      new Seed("액티비티", "볼링/당구/오락실", "🎳", 55, 50, 30, 50, 50),
      new Seed("액티비티", "수상레포츠", "🏄", 90, 50, 50, 50, 50),
      new Seed("액티비티", "캠핑/글램핑", "🏕️", 40, 50, 50, 50, 70),
      new Seed("액티비티", "원데이클래스", "🍳", 50, 60, 50, 55, 50),

      // 쇼핑 (2026-08-14 점수 확정)
      new Seed("쇼핑", "전통시장", "🏮", 65, 50, 35, 50, 50),
      new Seed("쇼핑", "야시장", "🌙", 70, 50, 50, 55, 50),
      new Seed("쇼핑", "소품/굿즈샵", "🕯️", 35, 50, 50, 70, 50),
      new Seed("쇼핑", "편집숍/빈티지샵", "👗", 50, 50, 60, 60, 50),
      new Seed("쇼핑", "백화점", "🏬", 50, 50, 65, 50, 50),
      new Seed("쇼핑", "아울렛", "🏷️", 50, 50, 30, 50, 50),
      new Seed("쇼핑", "플리마켓/팝업", "🧺", 60, 50, 50, 55, 50),

      // 축제 (2026-08-14 점수 확정)
      new Seed("축제", "계절축제", "🌸", 50, 50, 55, 70, 50),
      new Seed("축제", "불꽃/야간축제", "🎆", 75, 50, 60, 50, 50),
      new Seed("축제", "음식축제", "🥘", 60, 50, 30, 50, 50),
      new Seed("축제", "문화예술축제", "🎬", 50, 60, 50, 55, 50)
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
      placeCategoryRepository.save(category);
      updated++;
    }
    log.info("PlaceCategory 시드 반영 완료 (생성 {}건, 갱신 {}건, 정리 {}건)", created, updated, deleted);
  }
}

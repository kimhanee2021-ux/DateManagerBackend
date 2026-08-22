package org.ict.datemanagerbackend.domain.place.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.admin.service.AdminPlaceService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 2026-08-22 - PlaceCategoryLinker(이름 키워드)만으로는 전체 79,495건 중 약 5만 건이 그대로
 * 미분류로 남는데, NaverCategoryMatchService로 그걸 재분류하려면 네이버 API를 다시 호출해야
 * 해서 팀원 컴퓨터마다 시간이 오래 걸린다(장소 하나당 1회 호출 + 호출 간 텀). 그 결과(어느 장소가
 * 어느 세부분류인지)만 CSV로 저장소에 커밋해두고, 서버가 켜질 때 자동으로 읽어 반영한다 - 팀원은
 * git pull + 서버 재시작만 하면 되고, curl로 관리자 API를 직접 호출하거나 토큰을 발급받을 필요가
 * 없다. src/main/resources 아래 두는 이유는 data/ 폴더가 .gitignore에 걸려 있어서(원본 lodging
 * CSV처럼 큰 원본 데이터 전용) - 이 파일은 저장소에 커밋돼야 하므로 git이 추적하는 resources를 쓴다.
 *
 * <p>AdminPlaceServiceImpl.importPlaceCategoryLinksCsv()와 완전히 같은 로직을 재사용한다 -
 * (externalSource, externalId)로 장소를 찾고 (parentCategory, subCategory)로 분류를 찾아 연결하는
 * 방식이라, 이 컴퓨터의 place_categories 자동증가 id가 CSV를 만든 컴퓨터와 달라도 안전하다.
 * 파일이 없거나(팀원이 아직 새 CSV를 안 받은 경우) 비어 있으면 조용히 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(3)
public class PlaceCategoryLinksSeeder implements ApplicationRunner {

  private static final String RESOURCE_PATH = "place_category_links_seed.csv";

  private final AdminPlaceService adminPlaceService;

  @Override
  public void run(ApplicationArguments args) {
    ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
    if (!resource.exists()) {
      log.info("place_category_links_seed.csv 없음 - 장소↔분류 자동 시딩 건너뜀");
      return;
    }
    try (InputStream in = resource.getInputStream()) {
      String csv = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      var result = adminPlaceService.importPlaceCategoryLinksCsv(csv);
      log.info("place_category_links_seed.csv 자동 반영 완료 - {}", result);
    } catch (IOException e) {
      log.warn("place_category_links_seed.csv 읽기 실패", e);
    }
  }
}

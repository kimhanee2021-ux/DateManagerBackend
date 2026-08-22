package org.ict.datemanagerbackend.domain.place.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.admin.service.AdminPlaceService;
import org.ict.datemanagerbackend.domain.place.dto.PlaceDumpDto;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * 2026-08-22 - 팀원 컴퓨터의 Oracle DB가 테이블만 있고 place 데이터가 하나도 없는 상태(외부 API 키를
 * 아직 안 받았거나, KOPIS/TourAPI/카카오/네이버 동기화를 아직 한 번도 안 돌린 경우)라 코스빌더
 * 백엔드를 짜기 시작할 데이터 자체가 없다는 걸 확인했다. 기존 AdminPlaceController.exportFullDump/
 * importFullDump(2026-08-20, 팀원 공유용)는 관리자 로그인 + curl로 수동 호출해야 하는데, 이걸
 * 저장소에 커밋해서 서버 기동 시 자동으로 반영되게 한다 - PlaceCategoryLinksSeeder와 같은 이유.
 *
 * <p>원본 JSON이 79,495건 기준 41.5MB라 그대로 커밋하기엔 부담스러워서 gzip으로 압축해 넣었다
 * (4.3MB, 90% 감소) - 압축 안 해도 GitHub 자체 제한(파일당 100MB)엔 안 걸리지만, 저장소 용량과
 * clone 속도를 생각하면 압축하는 게 낫다.
 *
 * <p>PlaceCategoryLinker(장소→분류 자동 연결)가 place가 이미 있어야 의미가 있으므로, 반드시 그보다
 * 먼저 실행돼야 한다(@Order(0), PlaceCategorySeeder(1)/PlaceCategoryLinker(2)/
 * PlaceCategoryLinksSeeder(3)보다 앞).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class PlaceFullDumpSeeder implements ApplicationRunner {

  private static final String RESOURCE_PATH = "place_full_dump_seed.json.gz";

  // 이미 place 데이터가 있는 컴퓨터(실제 API 키로 동기화를 돌렸거나, 예전에 이 시더가 이미
  // 한 번 돌았던 경우)에서 매번 재시작마다 79,495건을 다시 통째로 훑는 건 낭비라(각 건마다
  // 조회+저장) 최초 1회만 돌게 개수 기준으로 가드를 둔다.
  private static final long SKIP_IF_PLACE_COUNT_AT_LEAST = 1000;

  private final AdminPlaceService adminPlaceService;
  private final PlaceRepository placeRepository;

  @Override
  public void run(ApplicationArguments args) {
    if (placeRepository.count() >= SKIP_IF_PLACE_COUNT_AT_LEAST) {
      log.info("이미 장소 데이터가 있어 place_full_dump_seed.json.gz 자동 시딩 건너뜀");
      return;
    }
    ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
    if (!resource.exists()) {
      log.info("place_full_dump_seed.json.gz 없음 - 장소 데이터 자동 시딩 건너뜀");
      return;
    }
    try (InputStream gzipIn = new GZIPInputStream(resource.getInputStream())) {
      List<PlaceDumpDto> dump = JsonMapper.builder().build()
          .readerForListOf(PlaceDumpDto.class)
          .readValue(gzipIn);
      var result = adminPlaceService.importFullDump(dump);
      log.info("place_full_dump_seed.json.gz 자동 반영 완료 - {}건 대상, 결과 {}", dump.size(), result);
    } catch (IOException e) {
      log.warn("place_full_dump_seed.json.gz 읽기 실패", e);
    }
  }
}

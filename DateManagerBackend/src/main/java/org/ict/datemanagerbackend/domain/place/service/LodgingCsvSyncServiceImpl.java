package org.ict.datemanagerbackend.domain.place.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.ict.datemanagerbackend.domain.place.entity.Place;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceStyleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 서울 열린데이터광장 "숙박업" 인허가 데이터를 places 테이블로 반영하는 서비스.
 *
 * <p>다른 *SyncService(TourAPI/KOPIS/네이버/카카오)와 달리 반복 호출 가능한 REST API가 아니라
 * 수시로 새로 내려받는 CSV 파일이라, 새 CSV를 받으면 {@code data/lodging.csv}에 덮어쓰고
 * {@code python data/convert_lodging_csv.py}로 위경도 변환까지 미리 끝낸 뒤(아래 참고), 관리자
 * 화면에서 이 서비스를 수동으로 다시 실행하는 방식으로 쓴다.
 *
 * <p>원본 CSV의 좌표는 위경도가 아니라 EPSG:5174(중부원점, Bessel 1841) 평면좌표라 그대로 쓰면
 * 안 된다. 처음엔 이 변환을 proj4j(자바)로 직접 했는데, 기존 DB의 검증된 좌표와 대조해보니
 * 약 350m씩 어긋나는 걸 발견했다 - proj4j에는 한국 좌표계 보정용 정밀 그리드가 없어서로 추정.
 * pyproj(파이썬, PROJ 공식 바인딩)로 같은 좌표를 변환하면 오차 0으로 정확히 일치해서, 좌표
 * 변환은 {@code convert_lodging_csv.py}가 미리 처리하고, 이 서비스는 이미 계산된 위경도가 담긴
 * {@code data/lodging_wgs84.csv}(관리번호,사업장명,주소,위도,경도)를 그대로 읽기만 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LodgingCsvSyncServiceImpl implements LodgingCsvSyncService {

  private static final String EXTERNAL_SOURCE = "LODGING_STD";
  private static final String CATEGORY = "숙박";

  private final PlaceRepository placeRepository;
  private final PlaceStyleRepository placeStyleRepository;
  private final PlaceDedupService placeDedupService;

  @Value("${place.lodging-csv-path}")
  private String csvPath;

  @Transactional
  @Override
  public Map<String, Integer> syncFromCsv() {
    Path path = Path.of(csvPath);
    if (!Files.exists(path)) {
      throw new IllegalStateException(
          "숙박업 CSV 파일을 찾을 수 없습니다: " + path.toAbsolutePath()
              + " (data/convert_lodging_csv.py를 먼저 실행했는지 확인)");
    }

    // 검색어 기반 동기화(카카오/네이버)와 같은 이유로 전체 교체 방식을 쓴다 - CSV 자체가 매번 그
    // 시점의 전체 스냅샷이라, 이번엔 없는(폐업했거나 목록에서 빠진) 예전 데이터가 계속 남지 않게 한다.
    // place_styles가 장소마다 자동 생성돼 있어 Place보다 먼저 지워야 FK 위반이 안 난다
    // (TourApiSyncService.cleanupBlacklistedPlaces와 같은 이유).
    placeStyleRepository.deleteByPlace_ExternalSource(EXTERNAL_SOURCE);
    placeRepository.deleteByExternalSource(EXTERNAL_SOURCE);

    int created = 0;
    int skippedDuplicate = 0;
    int skippedInvalid = 0;
    Set<String> seenThisRun = new HashSet<>();

    try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8);
         CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

      for (CSVRecord record : parser) {
        String managementNo = record.get("관리번호");
        if (managementNo == null || managementNo.isBlank() || !seenThisRun.add(managementNo)) {
          skippedInvalid++;
          continue;
        }

        String name = record.get("사업장명");
        String address = PlaceAddressNormalizer.fix(record.get("주소"));
        Double lat = parseCoordinate(record.get("위도"));
        Double lon = parseCoordinate(record.get("경도"));

        if (name == null || name.isBlank() || address == null || address.isBlank() || lat == null || lon == null) {
          skippedInvalid++;
          continue;
        }

        // 카카오의 AD5(숙박) 카테고리 등 다른 소스가 이미 저장해둔 같은 실제 호텔/모텔이면 새로 안 만든다.
        Optional<Place> duplicate = placeDedupService.findDuplicate(name, lat, lon);
        if (duplicate.isPresent()) {
          skippedDuplicate++;
          continue;
        }

        Place place = Place.builder()
            .name(name)
            .category(CATEGORY)
            .address(address)
            .latitude(lat)
            .longitude(lon)
            .externalSource(EXTERNAL_SOURCE)
            .externalId(managementNo)
            .build();
        placeRepository.save(place);
        created++;
      }
    } catch (IOException e) {
      throw new IllegalStateException("숙박업 CSV를 읽는 중 오류가 발생했습니다", e);
    }

    log.info("숙박업 CSV 동기화 완료 - 신규 {}건, 다른 소스와 중복이라 건너뜀 {}건, 값 누락으로 건너뜀 {}건",
        created, skippedDuplicate, skippedInvalid);
    return Map.of("created", created, "skippedDuplicate", skippedDuplicate, "skippedInvalid", skippedInvalid);
  }

  private Double parseCoordinate(String text) {
    if (text == null || text.isBlank()) return null;
    try {
      return Double.parseDouble(text.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

package org.ict.datemanagerbackend.domain.course.dto;

import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.course.entity.CourseItem;
import org.ict.datemanagerbackend.domain.place.entity.Place;

import java.util.List;

public record CourseGroupResponseWithCoords(
    Long id,
    String title,
    List<CourseItemDto> places
) {
  public static CourseGroupResponseWithCoords of(CourseGroup group, List<CourseItem> items) {
    List<CourseItemDto> courseItemDtos = items.stream()
        .map(CourseItemDto::from)
        .toList();

    return new CourseGroupResponseWithCoords(
        group.getId(),
        group.getTitle(),
        courseItemDtos
    );
  }

  // 코스에 속한 장소 상세 정보
  public record CourseItemDto(
      Long id,              // 프론트엔드에서 장소 선택/식별용으로 쓰이므로 포함하는 것을 추천합니다.
      Integer sequence,
      String name,
      String category,
      Double latitude,
      Double longitude,
      String address
  ) {
    public static CourseItemDto from(CourseItem item) {
      Place place = item.getPlace();
      return new CourseItemDto(
          place.getId(),          // Place PK ID
          item.getSequence(),      // CourseItem의 순서 (sequence)
          place.getName(),
          place.getCategory(),
          place.getLatitude(),
          place.getLongitude(),
          place.getAddress()
      );
    }
  }
}

package org.ict.datemanagerbackend.domain.course.dto;

import org.ict.datemanagerbackend.domain.course.entity.CourseGroup;
import org.ict.datemanagerbackend.domain.course.entity.CourseItem;
import org.ict.datemanagerbackend.domain.place.entity.Place;

import java.time.LocalDateTime;
import java.util.List;

public record CourseGroupResponseDto(
    Long id,
    String title,
    LocalDateTime createdAt,
    List<Item> items
) {
  public record Item(
      Long itemId,
      Integer sequence,
      Long placeId,
      String placeName,
      String category,
      String address,
      Double latitude,
      Double longitude,
      Integer transitTimeMinutes,
      Integer stayTimeMinutes
  ) {
    public static Item from(CourseItem item) {
      Place place = item.getPlace();
      return new Item(
          item.getId(), item.getSequence(), place.getId(), place.getName(), place.getCategory(),
          place.getAddress(), place.getLatitude(), place.getLongitude(),
          item.getTransitTimeMinutes(), item.getStayTimeMinutes()
      );
    }
  }

  public static CourseGroupResponseDto from(CourseGroup group, List<CourseItem> items) {
    return new CourseGroupResponseDto(
        group.getId(), group.getTitle(), group.getCreatedAt(),
        items.stream().map(Item::from).toList()
    );
  }
}

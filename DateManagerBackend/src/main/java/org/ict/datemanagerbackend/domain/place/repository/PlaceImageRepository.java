package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.PlaceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceImageRepository extends JpaRepository<PlaceImage, Long> {

  List<PlaceImage> findByPlace_IdIn(List<Long> placeIds);

  void deleteByPlace_Id(Long placeId);
}

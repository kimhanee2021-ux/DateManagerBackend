package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.PlaceLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceLikeRepository extends JpaRepository<PlaceLike, Long> {

  List<PlaceLike> findByUser_Id(Long userId);

  Optional<PlaceLike> findByUser_IdAndPlace_Id(Long userId, Long placeId);

  boolean existsByUser_IdAndPlace_Id(Long userId, Long placeId);
}

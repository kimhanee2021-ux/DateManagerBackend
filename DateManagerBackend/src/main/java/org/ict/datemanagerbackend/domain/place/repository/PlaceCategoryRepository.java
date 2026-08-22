package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceCategoryRepository extends JpaRepository<PlaceCategory, Long> {

  Optional<PlaceCategory> findByParentCategoryAndSubCategory(String parentCategory, String subCategory);

  List<PlaceCategory> findByParentCategory(String parentCategory);

}

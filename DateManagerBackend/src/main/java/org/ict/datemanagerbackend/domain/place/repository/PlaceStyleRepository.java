package org.ict.datemanagerbackend.domain.place.repository;

import org.ict.datemanagerbackend.domain.place.entity.PlaceStyle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceStyleRepository extends JpaRepository<PlaceStyle, Long> {

  // findByPlace_Id(Long) -> "WHERE place_id = ?"
  // PlaceStyle의 PK가 place 연관관계 자체(공유 PK)라, id로 바로 조회하는 대신 이 방식으로 찾는다.
  Optional<PlaceStyle> findByPlace_Id(Long placeId);

  // 목록 조회에서 장소 하나마다 따로 쿼리를 날리지 않도록(N+1 방지), 페이지에 담긴 place id들을
  // 한 번에 모아서 조회한다.
  List<PlaceStyle> findByPlace_IdIn(List<Long> placeIds);

  // 특정 소스(LODGING_STD 등)를 통째로 재동기화하기 전에, 그 장소들의 성향점수 행부터 먼저 지운다.
  // place_styles가 매 장소마다 자동 생성돼 있어서, 이걸 먼저 안 지우면 Place 삭제 시 FK 위반이 난다.
  void deleteByPlace_ExternalSource(String externalSource);

}

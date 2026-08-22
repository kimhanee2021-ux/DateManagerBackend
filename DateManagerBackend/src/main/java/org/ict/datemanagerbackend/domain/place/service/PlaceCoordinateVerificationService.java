package org.ict.datemanagerbackend.domain.place.service;

public interface PlaceCoordinateVerificationService {

  // TourAPI 출처 장소 전체를 주소 기반으로 카카오와 대조해 좌표를 재검증/교정한다.
  void verifyTourApiCoordinates();
}

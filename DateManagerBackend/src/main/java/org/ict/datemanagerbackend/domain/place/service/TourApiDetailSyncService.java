package org.ict.datemanagerbackend.domain.place.service;

// TourAPI detailIntro2(운영시간/휴무일/입장료/주차/편의시설) + detailImage2(사진 갤러리) 동기화 서비스.
public interface TourApiDetailSyncService {

  // <<장소 최대 limit건에 대해 상세정보/사진갤러리를 채운다 - 관리자 수동 트리거 또는 스케줄러가 호출>>
  DetailSyncResult syncDetails(int limit);

  record DetailSyncResult(int attempted, int filled, int noData, int failed) {
  }
}

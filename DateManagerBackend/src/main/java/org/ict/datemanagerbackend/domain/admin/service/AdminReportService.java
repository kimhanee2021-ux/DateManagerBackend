package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateReportRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminReportDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminReportService {
  Page<AdminReportDto> listReports(String status, Pageable pageable);
  AdminReportDto updateStatus(Long id, AdminUpdateReportRequest request);
  // <<처리 대기(PENDING) 신고 사유를 AI로 요약해서 반환 - 대기 건이 없으면 빈 문자열>>
  String summarizePendingReports();
}

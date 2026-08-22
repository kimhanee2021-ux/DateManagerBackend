package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateReportRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminReportDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminReportService {
  Page<AdminReportDto> listReports(String status, Pageable pageable);
  AdminReportDto updateStatus(Long id, AdminUpdateReportRequest request);
}

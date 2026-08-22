package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateReportRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminReportDto;
import org.ict.datemanagerbackend.domain.report.entity.Report;
import org.ict.datemanagerbackend.domain.report.repository.ReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class AdminReportServiceImpl implements AdminReportService {

  private static final Set<String> VALID_REPORT_STATUSES = Set.of("PENDING", "RESOLVED", "REJECTED");

  private final ReportRepository reportRepository;

  public AdminReportServiceImpl(ReportRepository reportRepository) {
    this.reportRepository = reportRepository;
  }

  @Override
  public Page<AdminReportDto> listReports(String status, Pageable pageable) {
    Page<Report> page = (status == null || status.isBlank())
        ? reportRepository.findAll(pageable)
        : reportRepository.findByStatus(status, pageable);
    return page.map(this::toDto);
  }

  @Override
  public AdminReportDto updateStatus(Long id, AdminUpdateReportRequest request) {
    if (request.status() == null || !VALID_REPORT_STATUSES.contains(request.status())) {
      throw new IllegalArgumentException("status는 PENDING, RESOLVED, REJECTED 중 하나여야 합니다");
    }
    Report report = reportRepository.findById(id).orElseThrow(() -> new NoSuchElementException("신고 내역을 찾을 수 없습니다"));
    report.setStatus(request.status());
    reportRepository.save(report);
    return toDto(report);
  }

  private AdminReportDto toDto(Report r) {
    return new AdminReportDto(r.getId(), r.getReporter().getId(), r.getReporter().getNickname(),
        r.getTargetType(), r.getTargetId(), r.getReason(), r.getStatus(), r.getCreatedAt());
  }
}

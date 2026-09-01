package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateReportRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminReportDto;
import org.ict.datemanagerbackend.domain.aichat.service.AiChatService;
import org.ict.datemanagerbackend.domain.report.entity.Report;
import org.ict.datemanagerbackend.domain.report.repository.ReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class AdminReportServiceImpl implements AdminReportService {

  private static final Set<String> VALID_REPORT_STATUSES = Set.of("PENDING", "RESOLVED", "REJECTED");

  private final ReportRepository reportRepository;
  private final AiChatService aiChatService;

  public AdminReportServiceImpl(ReportRepository reportRepository, AiChatService aiChatService) {
    this.reportRepository = reportRepository;
    this.aiChatService = aiChatService;
  }

  // "게시판 신고 요약"(2026-08-25 추가) - 신고가 쌓일수록 사유를 하나씩 읽는 게 번거로워서, OpenAI로
  // 비슷한 유형끼리 묶어 몇 줄 요약을 만들어준다. 신고 건수 자체가 지금은 적어 급한 기능은 아니지만
  // (5개 AI 기능 중 가장 낮은 우선순위로 논의됨), 요청대로 구현.
  @Override
  public String summarizePendingReports() {
    List<Report> pending = reportRepository.findByStatusOrderByCreatedAtDesc("PENDING");
    if (pending.isEmpty()) return "";
    List<String> reasons = pending.stream().map(Report::getReason).toList();
    return aiChatService.summarizeReports(reasons);
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

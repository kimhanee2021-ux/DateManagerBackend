package org.ict.datemanagerbackend.domain.report.controller;

import org.ict.datemanagerbackend.domain.report.entity.Report;
import org.ict.datemanagerbackend.domain.report.repository.ReportRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

// 신고 "접수"는 일반 로그인 사용자 누구나 할 수 있어서 여기(사용자용 컨트롤러)에 둔다.
// 신고 목록 조회/처리 같은 관리 기능은 다른 도메인과 동일하게 AdminController에 모아둔다.
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    private static final Set<String> VALID_TARGET_TYPES = Set.of("USER", "POST", "COMMENT");

    public ReportController(ReportRepository reportRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    public record CreateReportRequest(String targetType, Long targetId, String reason) {
    }

    public record ReportDto(Long id, Long reporterUserId, String targetType, Long targetId,
                             String reason, String status, LocalDateTime createdAt) {
    }

    @PostMapping
    public ResponseEntity<?> createReport(Authentication authentication, @RequestBody CreateReportRequest req) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        }
        Long userId = (Long) authentication.getPrincipal();
        User reporter = userRepository.findById(userId).orElse(null);
        if (reporter == null) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
        }
        if (req.targetType() == null || !VALID_TARGET_TYPES.contains(req.targetType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "targetType은 USER, POST, COMMENT 중 하나여야 합니다"));
        }
        if (req.targetId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "신고 대상(targetId)이 필요합니다"));
        }
        if (req.reason() == null || req.reason().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "신고 사유를 입력해주세요"));
        }
        Report report = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(req.targetType())
                .targetId(req.targetId())
                .reason(req.reason())
                .build());
        return ResponseEntity.ok(toDto(report));
    }

    private ReportDto toDto(Report r) {
        return new ReportDto(r.getId(), r.getReporter().getId(), r.getTargetType(), r.getTargetId(),
                r.getReason(), r.getStatus(), r.getCreatedAt());
    }
}

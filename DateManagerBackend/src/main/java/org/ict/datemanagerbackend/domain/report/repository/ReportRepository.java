package org.ict.datemanagerbackend.domain.report.repository;

import org.ict.datemanagerbackend.domain.report.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    Page<Report> findByStatus(String status, Pageable pageable);

    // 관리자 "신고 요약"(2026-08-25 추가)용 - 페이지 없이 처리 대기 중인 사유 전체를 한 번에.
    List<Report> findByStatusOrderByCreatedAtDesc(String status);
}

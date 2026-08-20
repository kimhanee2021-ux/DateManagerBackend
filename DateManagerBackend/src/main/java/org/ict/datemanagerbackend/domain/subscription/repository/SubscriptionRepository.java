package org.ict.datemanagerbackend.domain.subscription.repository;

import org.ict.datemanagerbackend.domain.subscription.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    // 사용자 화면(구독 조회/결제/해지)에서 "이 유저의 현재 구독"을 찾을 때 사용
    Optional<Subscription> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    // 자동 정기결제 스케줄러가 만료일 지난 ACTIVE 구독을 찾을 때 사용
    List<Subscription> findByStatusAndExpiresAtBefore(String status, LocalDateTime cutoff);

    boolean existsByUserIdAndStatus(Long userId, String status);

    // 대시보드 구독 증가 추이 집계용
    List<Subscription> findByStartedAtAfter(LocalDateTime cutoff);

    // 관리자 대시보드 "총 구독자수" 통계용
    long countByStatus(String status);

    // 관리자 구독 관리 탭 - 회원 이메일/닉네임 검색 (상태 필터 없음)
    @Query("SELECT s FROM Subscription s WHERE (:search IS NULL OR :search = '' "
            + "OR LOWER(s.user.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.user.nickname) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Subscription> searchAll(@Param("search") String search, Pageable pageable);

    // 관리자 구독 관리 탭 - 상태 필터(ACTIVE/CANCELED/EXPIRED) + 검색
    @Query("SELECT s FROM Subscription s WHERE s.status = :status AND (:search IS NULL OR :search = '' "
            + "OR LOWER(s.user.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.user.nickname) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Subscription> searchByStatus(@Param("status") String status, @Param("search") String search, Pageable pageable);
}

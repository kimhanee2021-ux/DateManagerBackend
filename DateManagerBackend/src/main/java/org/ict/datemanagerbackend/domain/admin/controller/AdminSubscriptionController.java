package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.admin.service.AdminAuthService;
import org.ict.datemanagerbackend.domain.subscription.entity.Subscription;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.subscription.repository.SubscriptionRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

// 아직 유저가 직접 구독을 신청하는 화면/결제 연동이 없어서, 지금은 관리자가 수동으로
// 구독을 만들고/바꾸고/취소하는 용도로 이 컨트롤러를 쓴다. 관리자 권한 체크는 AdminAuthService
// 공유 (예전엔 이 로직을 AdminController와 각자 복붙해서 갖고 있었음, 2026-08-18 정리).
@RestController
@RequestMapping("/api/admin/subscriptions")
public class AdminSubscriptionController {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "CANCELED", "EXPIRED");

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final AdminAuthService adminAuthService;

    public AdminSubscriptionController(SubscriptionRepository subscriptionRepository, UserRepository userRepository,
                                        AdminAuthService adminAuthService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.adminAuthService = adminAuthService;
    }

    public record AdminSubscriptionDto(Long id, Long userId, String userEmail, String userNickname,
                                        String planCode, String status, LocalDateTime startedAt,
                                        LocalDateTime expiresAt, String paymentProvider) {
    }

    public record CreateSubscriptionRequest(String userEmail, String planCode) {
    }

    public record UpdateSubscriptionRequest(String planCode, String status, LocalDateTime expiresAt, String paymentProvider) {
    }

    private boolean isAdmin(Authentication authentication) {
        return adminAuthService.isAdmin(authentication);
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false, defaultValue = "all") String status,
                                   @PageableDefault(size = 15, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        Page<Subscription> page = "all".equalsIgnoreCase(status)
                ? subscriptionRepository.searchAll(search, pageable)
                : subscriptionRepository.searchByStatus(status.toUpperCase(), search, pageable);
        return ResponseEntity.ok(page.map(this::toDto));
    }

    @PostMapping
    public ResponseEntity<?> create(Authentication authentication, @RequestBody CreateSubscriptionRequest req) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        if (req.userEmail() == null || req.userEmail().isBlank() || req.planCode() == null || req.planCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "회원 이메일과 플랜을 입력해주세요"));
        }
        User user = userRepository.findByEmail(req.userEmail().trim()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "해당 이메일의 회원을 찾을 수 없습니다"));
        }
        Subscription saved = subscriptionRepository.save(Subscription.builder()
                .user(user)
                .planCode(req.planCode())
                .build());
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(Authentication authentication, @PathVariable Long id,
                                     @RequestBody UpdateSubscriptionRequest req) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        Subscription sub = subscriptionRepository.findById(id).orElse(null);
        if (sub == null) {
            return ResponseEntity.status(404).body(Map.of("error", "구독을 찾을 수 없습니다"));
        }
        if (req.status() != null && !req.status().isBlank()) {
            if (!VALID_STATUSES.contains(req.status())) {
                return ResponseEntity.badRequest().body(Map.of("error", "status는 ACTIVE/CANCELED/EXPIRED 중 하나여야 합니다"));
            }
            sub.setStatus(req.status());
        }
        if (req.planCode() != null && !req.planCode().isBlank()) {
            sub.setPlanCode(req.planCode());
        }
        if (req.expiresAt() != null) {
            sub.setExpiresAt(req.expiresAt());
        }
        if (req.paymentProvider() != null) {
            sub.setPaymentProvider(req.paymentProvider());
        }
        subscriptionRepository.save(sub);
        return ResponseEntity.ok(toDto(sub));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long id) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        if (!subscriptionRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "구독을 찾을 수 없습니다"));
        }
        subscriptionRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private AdminSubscriptionDto toDto(Subscription s) {
        return new AdminSubscriptionDto(s.getId(), s.getUser().getId(), s.getUser().getEmail(), s.getUser().getNickname(),
                s.getPlanCode(), s.getStatus(), s.getStartedAt(), s.getExpiresAt(), s.getPaymentProvider());
    }
}

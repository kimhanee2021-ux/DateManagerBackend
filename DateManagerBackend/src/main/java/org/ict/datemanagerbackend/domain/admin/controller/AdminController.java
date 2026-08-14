package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleMember;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.service.CultureEventSyncService;
import org.ict.datemanagerbackend.domain.place.service.MuseumSyncService;
import org.ict.datemanagerbackend.domain.place.service.NaverPlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.PlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.TourApiSyncService;
import org.ict.datemanagerbackend.domain.report.entity.Report;
import org.ict.datemanagerbackend.domain.report.repository.ReportRepository;
import org.ict.datemanagerbackend.domain.user.entity.LoginLog;
import org.ict.datemanagerbackend.domain.user.entity.Subscription;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.LoginLogRepository;
import org.ict.datemanagerbackend.domain.user.repository.SubscriptionRepository;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 관리자가 한 명뿐이라 별도 role 체계 없이, application.yaml의 app.admin-email과
// 로그인한 유저의 이메일이 같은지만 확인하는 방식으로 관리자 권한을 처리한다.
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final CoupleRepository coupleRepository;
    private final CoupleMemberRepository coupleMemberRepository;
    private final PlaceRepository placeRepository;
    private final PlaceSyncService placeSyncService;
    private final TourApiSyncService tourApiSyncService;
    private final MuseumSyncService museumSyncService;
    private final NaverPlaceSyncService naverPlaceSyncService;
    private final CultureEventSyncService cultureEventSyncService;
    private final ReportRepository reportRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LoginLogRepository loginLogRepository;

    @Value("${app.admin-email}")
    private String adminEmail;

    public AdminController(UserRepository userRepository, CoupleRepository coupleRepository,
                            CoupleMemberRepository coupleMemberRepository, PlaceRepository placeRepository,
                            PlaceSyncService placeSyncService, TourApiSyncService tourApiSyncService,
                            MuseumSyncService museumSyncService, NaverPlaceSyncService naverPlaceSyncService,
                            CultureEventSyncService cultureEventSyncService,
                            ReportRepository reportRepository,
                            SubscriptionRepository subscriptionRepository, LoginLogRepository loginLogRepository) {
        this.userRepository = userRepository;
        this.coupleRepository = coupleRepository;
        this.coupleMemberRepository = coupleMemberRepository;
        this.placeRepository = placeRepository;
        this.placeSyncService = placeSyncService;
        this.tourApiSyncService = tourApiSyncService;
        this.museumSyncService = museumSyncService;
        this.naverPlaceSyncService = naverPlaceSyncService;
        this.cultureEventSyncService = cultureEventSyncService;
        this.reportRepository = reportRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.loginLogRepository = loginLogRepository;
    }

    public record DailyCountDto(String date, long count) {
    }

    public record GenderBreakdownDto(long male, long female, long unknown) {
    }

    public record DashboardStatsDto(long totalUsers, long totalSubscribers, long totalCouples, long todayVisitors,
                                     List<DailyCountDto> visitorTrend, List<DailyCountDto> subscriptionTrend,
                                     GenderBreakdownDto genderBreakdown) {
    }

    public record AdminUserDto(Long id, String email, String nickname, String gender, LocalDateTime createdAt, boolean subscribed) {
    }

    public record AdminCoupleMemberDto(Long userId, String nickname, String email, String roleType, boolean subscribed) {
    }

    public record AdminCoupleDto(Long id, String status, LocalDateTime connectedAt, List<AdminCoupleMemberDto> members) {
    }

    public record AdminUpdateUserRequest(String nickname, String gender) {
    }

    public record AdminUpdateCoupleRequest(String status) {
    }

    public record AdminReportDto(Long id, Long reporterUserId, String reporterNickname, String targetType,
                                  Long targetId, String reason, String status, LocalDateTime createdAt) {
    }

    public record AdminUpdateReportRequest(String status) {
    }

    private static final Set<String> VALID_COUPLE_STATUSES = Set.of("ACTIVE", "DISCONNECTED");
    private static final Set<String> VALID_REPORT_STATUSES = Set.of("PENDING", "RESOLVED", "REJECTED");

    private User currentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return userRepository.findById(userId).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && user.getEmail() != null
                && !adminEmail.isBlank()
                && user.getEmail().equalsIgnoreCase(adminEmail);
    }

    @GetMapping("/check")
    public ResponseEntity<?> check(Authentication authentication) {
        return ResponseEntity.ok(Map.of("isAdmin", isAdmin(currentUser(authentication))));
    }

    // 관리자 홈 대시보드용 통계 + 최근 7일 방문자/구독 증가 추이.
    // 방문자는 LoginLog(로그인 성공마다 기록됨)를, 구독 증가는 Subscription.startedAt을 날짜별로 묶어서 센다.
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication authentication) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }

        LocalDate today = LocalDate.now();
        LocalDateTime windowStart = today.minusDays(6).atStartOfDay();

        long totalUsers = userRepository.countByWithdrawnAtIsNull();
        long totalSubscribers = subscriptionRepository.countByStatus("ACTIVE");
        // count()로 전체를 세면 연결 해제(DISCONNECTED)된 예전 커플까지 다 포함돼서 실제
        // "매칭된 커플" 수보다 부풀려진다 - ACTIVE만 센다.
        long totalCouples = coupleRepository.countByStatus("ACTIVE");

        Map<LocalDate, Set<Long>> visitorsByDay = new HashMap<>();
        for (LoginLog log : loginLogRepository.findByLoggedInAtAfter(windowStart)) {
            LocalDate day = log.getLoggedInAt().toLocalDate();
            visitorsByDay.computeIfAbsent(day, d -> new HashSet<>()).add(log.getUser().getId());
        }
        long todayVisitors = visitorsByDay.getOrDefault(today, Set.of()).size();
        List<DailyCountDto> visitorTrend = buildTrend(today, day -> (long) visitorsByDay.getOrDefault(day, Set.of()).size());

        Map<LocalDate, Long> subsByDay = subscriptionRepository.findByStartedAtAfter(windowStart).stream()
                .filter(s -> s.getStartedAt() != null)
                .collect(Collectors.groupingBy(s -> s.getStartedAt().toLocalDate(), Collectors.counting()));
        List<DailyCountDto> subscriptionTrend = buildTrend(today, day -> subsByDay.getOrDefault(day, 0L));

        GenderBreakdownDto genderBreakdown = new GenderBreakdownDto(
                userRepository.countByWithdrawnAtIsNullAndGender("MALE"),
                userRepository.countByWithdrawnAtIsNullAndGender("FEMALE"),
                userRepository.countByWithdrawnAtIsNullAndGender("UNKNOWN"));

        return ResponseEntity.ok(new DashboardStatsDto(totalUsers, totalSubscribers, totalCouples, todayVisitors,
                visitorTrend, subscriptionTrend, genderBreakdown));
    }

    private List<DailyCountDto> buildTrend(LocalDate endDay, Function<LocalDate, Long> counter) {
        List<DailyCountDto> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = endDay.minusDays(i);
            trend.add(new DailyCountDto(day.toString(), counter.apply(day)));
        }
        return trend;
    }

    // 가입일 내림차순 15명씩 페이지네이션 + 이메일/닉네임 검색 + 일반/구독회원 필터. 탈퇴 회원은 항상 제외.
    // app.yaml의 one-indexed-parameters 설정 덕분에 page 쿼리파라미터는 1부터 시작한다.
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(Authentication authentication,
                                        @RequestParam(required = false) String search,
                                        @RequestParam(required = false, defaultValue = "email") String field,
                                        @RequestParam(required = false, defaultValue = "all") String filter,
                                        @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        Page<User> page = switch (filter) {
            case "subscribed" -> userRepository.searchActiveSubscribed(search, field, pageable);
            case "free" -> userRepository.searchActiveFree(search, field, pageable);
            default -> userRepository.searchActive(search, field, pageable);
        };
        Page<AdminUserDto> dtoPage = page.map(this::toUserDto);
        return ResponseEntity.ok(dtoPage);
    }

    // filter: all(기본) / subscribed(멤버 중 구독자 있는 커플) / free(멤버 전원 비구독) - 구독 여부 기준
    // status: active(기본) / all - 연결 상태 기준. 기본은 해제된 예전 커플을 안 보여주되,
    // 관리자가 이력까지 보고 싶을 때는 status=all로 명시적으로 요청하게 한다.
    @GetMapping("/couples")
    public ResponseEntity<?> listCouples(Authentication authentication,
                                          @RequestParam(required = false, defaultValue = "all") String filter,
                                          @RequestParam(required = false, defaultValue = "active") String status) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        List<AdminCoupleDto> couples = coupleRepository.findAll().stream()
                .filter(c -> "all".equals(status) || "ACTIVE".equals(c.getStatus()))
                .map(this::toCoupleDto)
                .filter(dto -> matchesCoupleFilter(dto, filter))
                .toList();
        return ResponseEntity.ok(couples);
    }

    private boolean matchesCoupleFilter(AdminCoupleDto dto, String filter) {
        boolean anySubscribed = dto.members().stream().anyMatch(AdminCoupleMemberDto::subscribed);
        return switch (filter) {
            case "subscribed" -> anySubscribed;
            case "free" -> !anySubscribed;
            default -> true;
        };
    }

    private AdminUserDto toUserDto(User u) {
        boolean subscribed = subscriptionRepository.existsByUserIdAndStatus(u.getId(), "ACTIVE");
        return new AdminUserDto(u.getId(), u.getEmail(), u.getNickname(), u.getGender(), u.getCreatedAt(), subscribed);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(Authentication authentication, @PathVariable Long id,
                                         @RequestBody AdminUpdateUserRequest req) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "회원을 찾을 수 없습니다"));
        }
        if (req.nickname() != null && !req.nickname().isBlank()) {
            user.setNickname(req.nickname());
        }
        if (req.gender() != null && !req.gender().isBlank()) {
            user.setGender(req.gender());
        }
        userRepository.save(user);
        return ResponseEntity.ok(toUserDto(user));
    }

    // 실제 row를 지우지 않고 withdrawnAt만 채우는 탈퇴 처리(soft-delete)로 바꿨다.
    // 탈퇴 후 1년 지난 계정의 실제 삭제는 WithdrawnUserCleanupService가 배치로 처리한다.
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(Authentication authentication, @PathVariable Long id) {
        User me = currentUser(authentication);
        if (!isAdmin(me)) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        if (me.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "관리자 본인 계정은 탈퇴 처리할 수 없습니다"));
        }
        User target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("error", "회원을 찾을 수 없습니다"));
        }
        if (target.getWithdrawnAt() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 탈퇴 처리된 회원입니다"));
        }
        target.setWithdrawnAt(LocalDateTime.now());
        userRepository.save(target);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/couples/{id}")
    public ResponseEntity<?> updateCouple(Authentication authentication, @PathVariable Long id,
                                           @RequestBody AdminUpdateCoupleRequest req) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        if (req.status() == null || !VALID_COUPLE_STATUSES.contains(req.status())) {
            return ResponseEntity.badRequest().body(Map.of("error", "status는 ACTIVE 또는 DISCONNECTED 여야 합니다"));
        }
        Couple couple = coupleRepository.findById(id).orElse(null);
        if (couple == null) {
            return ResponseEntity.status(404).body(Map.of("error", "커플을 찾을 수 없습니다"));
        }
        boolean reactivating = "ACTIVE".equals(req.status());
        LocalDateTime now = LocalDateTime.now();
        couple.setStatus(req.status());
        if (reactivating) {
            couple.setConnectedAt(now);
            // 당사자들에게 "관리자가 방금 다시 연결해줬다"는 걸 보여주기 위한 플래그.
            // 마이페이지/커플싱크탭이 이 값이 채워져 있으면 알림 배너를 띄우고,
            // 확인하면 ack-admin-reconnect 엔드포인트가 다시 null로 되돌린다.
            couple.setReconnectedByAdminAt(now);
        } else {
            // 해제할 땐 예전에 안 지워진 재연결 알림이 있었다면 같이 정리해준다(의미 없는 값이라).
            couple.setReconnectedByAdminAt(null);
        }
        coupleRepository.save(couple);

        // 이용자 개인 화면(마이페이지/커플싱크탭)은 Couple.status가 아니라 CoupleMember.left_at으로
        // 연결 여부를 판단한다(findByUser_IdAndLeftAtIsNull). 여기서 status만 바꾸고 left_at을
        // 그대로 두면, 관리자 화면엔 "연결됨"으로 보여도 정작 당사자 화면엔 "연결 안 됨"으로 보이는
        // (혹은 그 반대) 불일치가 생긴다 - 두 값이 항상 같이 움직이도록 여기서 맞춰준다.
        for (CoupleMember member : coupleMemberRepository.findByCoupleId(id)) {
            member.setLeftAt(reactivating ? null : now);
            coupleMemberRepository.save(member);
        }

        return ResponseEntity.ok(toCoupleDto(couple));
    }

    @DeleteMapping("/couples/{id}")
    public ResponseEntity<?> deleteCouple(Authentication authentication, @PathVariable Long id) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        if (!coupleRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "커플을 찾을 수 없습니다"));
        }
        try {
            List<CoupleMember> members = coupleMemberRepository.findByCoupleId(id);
            coupleMemberRepository.deleteAll(members);
            coupleRepository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            // 커플 기념일/AI채팅/코스 등 연결된 데이터가 있으면 FK 제약으로 삭제가 막힌다
            return ResponseEntity.status(409).body(Map.of("error", "이 커플은 기념일·채팅 등 연결된 데이터가 있어 삭제할 수 없습니다"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/reports")
    public ResponseEntity<?> listReports(Authentication authentication,
                                          @RequestParam(required = false) String status,
                                          @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        Page<Report> page = (status == null || status.isBlank())
                ? reportRepository.findAll(pageable)
                : reportRepository.findByStatus(status, pageable);
        return ResponseEntity.ok(page.map(this::toReportDto));
    }

    @PutMapping("/reports/{id}")
    public ResponseEntity<?> updateReportStatus(Authentication authentication, @PathVariable Long id,
                                                 @RequestBody AdminUpdateReportRequest req) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        if (req.status() == null || !VALID_REPORT_STATUSES.contains(req.status())) {
            return ResponseEntity.badRequest().body(Map.of("error", "status는 PENDING, RESOLVED, REJECTED 중 하나여야 합니다"));
        }
        Report report = reportRepository.findById(id).orElse(null);
        if (report == null) {
            return ResponseEntity.status(404).body(Map.of("error", "신고 내역을 찾을 수 없습니다"));
        }
        report.setStatus(req.status());
        reportRepository.save(report);
        return ResponseEntity.ok(toReportDto(report));
    }

    private AdminReportDto toReportDto(Report r) {
        return new AdminReportDto(r.getId(), r.getReporter().getId(), r.getReporter().getNickname(),
                r.getTargetType(), r.getTargetId(), r.getReason(), r.getStatus(), r.getCreatedAt());
    }

    private AdminCoupleDto toCoupleDto(Couple couple) {
        List<AdminCoupleMemberDto> members = coupleMemberRepository.findByCoupleId(couple.getId()).stream()
                .map(cm -> new AdminCoupleMemberDto(cm.getUser().getId(), cm.getUser().getNickname(),
                        cm.getUser().getEmail(), cm.getRoleType(),
                        subscriptionRepository.existsByUserIdAndStatus(cm.getUser().getId(), "ACTIVE")))
                .toList();
        return new AdminCoupleDto(couple.getId(), couple.getStatus(), couple.getConnectedAt(), members);
    }

    // 장소 데이터 동기화는 원래 매일 새벽에 자동(@Scheduled)으로만 도는데, 개발 중 수동으로
    // 바로 실행해서 결과를 확인하고 싶을 때 쓰는 관리자 전용 트리거. 외부 API를 여러 번
    // 호출하느라 응답이 오래 걸릴 수 있어(수십 초~수 분) 동기 방식으로 그대로 기다린다.
    @PostMapping("/places/sync/{source}")
    public ResponseEntity<?> triggerPlaceSync(Authentication authentication, @PathVariable String source) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        try {
            switch (source) {
                case "kopis" -> placeSyncService.syncPerformances();
                case "festival" -> placeSyncService.syncFestivals();
                case "tourapi" -> tourApiSyncService.syncPlaces();
                case "museum" -> museumSyncService.syncMuseums();
                case "naver" -> naverPlaceSyncService.syncPlaces();
                case "cultureinfo" -> cultureEventSyncService.syncEvents();
                default -> {
                    return ResponseEntity.badRequest().body(Map.of("error", "source는 kopis, festival, tourapi, museum, naver, cultureinfo 중 하나여야 합니다"));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "동기화 중 오류가 발생했습니다: " + e.getMessage()));
        }
        return placesSyncStatus();
    }

    @GetMapping("/places/sync-status")
    public ResponseEntity<?> placesSyncStatusEndpoint(Authentication authentication) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        return placesSyncStatus();
    }

    private ResponseEntity<?> placesSyncStatus() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : placeRepository.countGroupedByCategory()) {
            counts.put((String) row[0], (Long) row[1]);
        }
        counts.put("전체", placeRepository.count());
        return ResponseEntity.ok(counts);
    }
}

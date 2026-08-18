package org.ict.datemanagerbackend.domain.admin.controller;

import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleMember;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.place.entity.PlaceCategory;
import org.ict.datemanagerbackend.domain.place.repository.PlaceCategoryRepository;
import org.ict.datemanagerbackend.domain.place.repository.PlaceRepository;
import org.ict.datemanagerbackend.domain.place.service.CultureEventSyncService;
import org.ict.datemanagerbackend.domain.place.service.KakaoPlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.LodgingCsvSyncService;
import org.ict.datemanagerbackend.domain.place.service.MuseumSyncService;
import org.ict.datemanagerbackend.domain.place.service.NaverPlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.PlaceSyncService;
import org.ict.datemanagerbackend.domain.place.service.TourApiSyncService;
import org.ict.datemanagerbackend.domain.report.entity.Report;
import org.ict.datemanagerbackend.domain.report.repository.ReportRepository;
import org.ict.datemanagerbackend.domain.user.entity.LoginLog;
import org.ict.datemanagerbackend.domain.subscription.entity.Subscription;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.LoginLogRepository;
import org.ict.datemanagerbackend.domain.subscription.repository.SubscriptionRepository;
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
    private final PlaceCategoryRepository placeCategoryRepository;
    private final PlaceSyncService placeSyncService;
    private final TourApiSyncService tourApiSyncService;
    private final MuseumSyncService museumSyncService;
    private final NaverPlaceSyncService naverPlaceSyncService;
    private final KakaoPlaceSyncService kakaoPlaceSyncService;
    private final LodgingCsvSyncService lodgingCsvSyncService;
    private final CultureEventSyncService cultureEventSyncService;
    private final ReportRepository reportRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LoginLogRepository loginLogRepository;

    @Value("${app.admin-email}")
    private String adminEmail;

    public AdminController(UserRepository userRepository, CoupleRepository coupleRepository,
                            CoupleMemberRepository coupleMemberRepository, PlaceRepository placeRepository,
                            PlaceCategoryRepository placeCategoryRepository,
                            PlaceSyncService placeSyncService, TourApiSyncService tourApiSyncService,
                            MuseumSyncService museumSyncService, NaverPlaceSyncService naverPlaceSyncService,
                            KakaoPlaceSyncService kakaoPlaceSyncService,
                            LodgingCsvSyncService lodgingCsvSyncService,
                            CultureEventSyncService cultureEventSyncService,
                            ReportRepository reportRepository,
                            SubscriptionRepository subscriptionRepository, LoginLogRepository loginLogRepository) {
        this.userRepository = userRepository;
        this.coupleRepository = coupleRepository;
        this.coupleMemberRepository = coupleMemberRepository;
        this.placeRepository = placeRepository;
        this.placeCategoryRepository = placeCategoryRepository;
        this.placeSyncService = placeSyncService;
        this.tourApiSyncService = tourApiSyncService;
        this.museumSyncService = museumSyncService;
        this.naverPlaceSyncService = naverPlaceSyncService;
        this.kakaoPlaceSyncService = kakaoPlaceSyncService;
        this.lodgingCsvSyncService = lodgingCsvSyncService;
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
        long totalCouples = coupleRepository.count();

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

    // filter: all(기본) / subscribed(멤버 중 구독자 있는 커플) / free(멤버 전원 비구독)
    @GetMapping("/couples")
    public ResponseEntity<?> listCouples(Authentication authentication,
                                          @RequestParam(required = false, defaultValue = "all") String filter) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        List<AdminCoupleDto> couples = coupleRepository.findAll().stream()
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
        couple.setStatus(req.status());
        coupleRepository.save(couple);
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
                case "kakao" -> kakaoPlaceSyncService.syncPlaces();
                case "cultureinfo" -> cultureEventSyncService.syncEvents();
                case "lodging" -> lodgingCsvSyncService.syncFromCsv();
                default -> {
                    return ResponseEntity.badRequest().body(Map.of("error", "source는 kopis, festival, tourapi, museum, naver, kakao, cultureinfo, lodging 중 하나여야 합니다"));
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "동기화 중 오류가 발생했습니다: " + e.getMessage()));
        }
        return placesSyncStatus();
    }

    // 블랙리스트 필터 도입(2026-08-14) 이전에 이미 저장된 프랜차이즈/체인점(올리브영 등)을 한 번에 정리.
    @PostMapping("/places/cleanup-blacklist")
    public ResponseEntity<?> cleanupBlacklistedPlaces(Authentication authentication) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body(Map.of("error", "관리자만 접근할 수 있습니다"));
        }
        return ResponseEntity.ok(tourApiSyncService.cleanupBlacklistedPlaces());
    }

    // 빅데이터 분석/외부 업데이트를 위한 전체 장소 백업(CSV). 8만여 건이라 엔티티로 로드하지 않고
    // PlaceRepository.findAllForExport()의 프로젝션 쿼리 한 방으로 필요한 컬럼만 가져온다(2026-08-14).
    @GetMapping("/places/export")
    public ResponseEntity<String> exportPlaces(Authentication authentication) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body("관리자만 접근할 수 있습니다");
        }
        StringBuilder csv = new StringBuilder();
        csv.append("id,name,category,subCategory,emoji,address,latitude,longitude,externalSource,")
           .append("scoreEnergy,scoreImmersion,scoreVibe,scoreAesthetic,scoreDepth\n");
        for (Object[] row : placeRepository.findAllForExport()) {
            csv.append(row[0]).append(',')
               .append(csvEscape((String) row[1])).append(',')
               .append(csvEscape((String) row[2])).append(',')
               .append(csvEscape((String) row[3])).append(',')
               .append(csvEscape((String) row[4])).append(',')
               .append(csvEscape((String) row[5])).append(',')
               .append(row[6] == null ? "" : row[6]).append(',')
               .append(row[7] == null ? "" : row[7]).append(',')
               .append(csvEscape((String) row[8])).append(',')
               .append(row[9] == null ? "" : row[9]).append(',')
               .append(row[10] == null ? "" : row[10]).append(',')
               .append(row[11] == null ? "" : row[11]).append(',')
               .append(row[12] == null ? "" : row[12]).append(',')
               .append(row[13] == null ? "" : row[13]).append('\n');
        }
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=places_export.csv")
                .body(csv.toString());
    }

    // place_categories(9개 대분류 x 48개 세부분류 성향점수 공식) 전체 백업(CSV).
    @GetMapping("/places/categories/export")
    public ResponseEntity<String> exportPlaceCategories(Authentication authentication) {
        if (!isAdmin(currentUser(authentication))) {
            return ResponseEntity.status(403).body("관리자만 접근할 수 있습니다");
        }
        StringBuilder csv = new StringBuilder();
        csv.append("id,parentCategory,subCategory,emoji,scoreEnergy,scoreImmersion,scoreVibe,scoreAesthetic,scoreDepth,isIndoor,isActivity\n");
        for (PlaceCategory pc : placeCategoryRepository.findAll()) {
            csv.append(pc.getId()).append(',')
               .append(csvEscape(pc.getParentCategory())).append(',')
               .append(csvEscape(pc.getSubCategory())).append(',')
               .append(csvEscape(pc.getEmoji())).append(',')
               .append(pc.getScoreEnergy()).append(',')
               .append(pc.getScoreImmersion()).append(',')
               .append(pc.getScoreVibe()).append(',')
               .append(pc.getScoreAesthetic()).append(',')
               .append(pc.getScoreDepth()).append(',')
               .append(pc.getIsIndoor()).append(',')
               .append(pc.getIsActivity()).append('\n');
        }
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=place_categories_export.csv")
                .body(csv.toString());
    }

    // CSV 필드에 콤마/따옴표/줄바꿈이 섞여 있어도(장소명·주소에 흔함) 깨지지 않게 감싸는 이스케이프.
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

package org.ict.datemanagerbackend.domain.couple.controller;

import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleInvite;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleMember;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleInviteRepository;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 커플 초대 링크 생성/수락, 연결 상태 조회, 연결 해제를 담당하는 컨트롤러.
 *
 * <p><b>전체 흐름(state machine)</b>
 * <pre>
 *   [A: 초대 생성]                [B: 링크 수락]                  [연결 해제]
 *   CoupleInvite                  CoupleInvite.status            Couple.status
 *   status=PENDING     ---->      =ACCEPTED               ---->  =DISCONNECTED
 *   (1시간 유효)                   + Couple 1건 생성                + CoupleMember 양쪽
 *                                  + CoupleMember 2건 생성            left_at 기록
 * </pre>
 *
 * <p><b>왜 Couple/CoupleMember를 바로 안 만들고 CoupleInvite를 거치는가</b>
 * A가 초대를 누르는 시점엔 상대(B)가 누구인지 서버가 전혀 모른다(로그인조차 안 했을 수 있음).
 * 그래서 "누구든 이 토큰을 아는 사람과 연결하겠다"는 의미의 임시 티켓(CoupleInvite)만 먼저 만들고,
 * 실제로 B가 로그인한 채로 그 토큰을 들고 오는 순간(accept) 비로소 Couple/CoupleMember를 생성한다.
 * 토큰 없이 "user_id로 바로 연결" 방식을 쓰면 남의 ID를 추측해서 임의로 연결 요청을 보낼 수 있어 위험하다.
 */
@RestController
@RequestMapping("/api/couple")
public class CoupleController {

  // 초대 링크의 유효 기간. 프론트(CoupleTab.jsx)의 "링크는 1시간 동안 유효합니다" 문구와 반드시 맞춰야 함.
  private static final long INVITE_EXPIRES_HOURS = 1;

  private final CoupleRepository coupleRepository;
  private final CoupleMemberRepository coupleMemberRepository;
  private final CoupleInviteRepository coupleInviteRepository;
  private final UserRepository userRepository;

  // application.yaml의 app.frontend-base-url을 그대로 주입받아, 초대 토큰을 실제 클릭 가능한
  // URL(프론트 주소 + /couple/connect/{token})로 조립하는 데 사용한다.
  @Value("${app.frontend-base-url}")
  private String frontendBaseUrl;

  // 생성자 주입: Spring이 스프링 컨테이너에 등록된 Repository/UserRepository 빈들을
  // 이 컨트롤러가 만들어질 때 자동으로 찾아서 넣어준다(의존성 주입, DI).
  public CoupleController(CoupleRepository coupleRepository,
                           CoupleMemberRepository coupleMemberRepository,
                           CoupleInviteRepository coupleInviteRepository,
                           UserRepository userRepository) {
    this.coupleRepository = coupleRepository;
    this.coupleMemberRepository = coupleMemberRepository;
    this.coupleInviteRepository = coupleInviteRepository;
    this.userRepository = userRepository;
  }

  // ── 응답 DTO들 ──────────────────────────────────────────────────────────
  // record: 필드를 선언하면 생성자/getter(필드명() 형태)/equals/hashCode/toString이 자동으로 만들어지는
  // 자바 문법. 응답 전용의 "값만 담는" 객체라 엔티티(Entity)를 그대로 내려주지 않고 이렇게 별도로 만든다.
  // (엔티티를 그대로 JSON으로 내려주면 연관된 다른 엔티티까지 줄줄이 직렬화되거나, DB 컬럼 구조가
  //  API 응답 형태에 그대로 노출되는 문제가 생길 수 있어서 DTO로 한 번 감싸는 것이 일반적인 관례다.)

  // 초대 생성(POST /invite) 응답: 발급된 토큰, 프론트가 바로 쓸 수 있는 전체 링크, 만료 시각
  public record InviteResponse(String token, String inviteUrl, LocalDateTime expiresAt) {
  }

  // 상대방 정보. profileImageUrl이 있으면 프론트가 그대로 쓰고, 없으면 gender 기준 기본 이미지로
  // 대체한다(프론트 Avatar 컴포넌트 참고 - 업로드 사진 우선, 없으면 성별 기본 이미지).
  public record PartnerDto(Long userId, String nickname, String email, String gender, String profileImageUrl) {
  }

  // 연결 상태 조회(GET /status) 응답. 연결 안 됐으면 partner/connectedAt/metDate는 null로 내려간다.
  // metDate는 connectedAt(이 사이트에서 연결된 시각)과 별개로, 두 사람이 실제로 처음 만난 날짜다.
  public record StatusResponse(boolean linked, PartnerDto partner, LocalDateTime connectedAt, LocalDate metDate) {
  }

  // 만난 날짜 등록/수정(PUT /met-date) 요청. metDate가 null이면 등록 취소(초기화)로 처리한다.
  public record MetDateRequest(LocalDate metDate) {
  }

  // ── 내부 헬퍼 메서드 ────────────────────────────────────────────────────

  /**
   * 로그인한 사용자의 User 엔티티를 조회한다.
   * Authentication.getPrincipal()에는 JwtAuthFilter가 JWT를 검증하면서 꺼내 넣어둔 "userId(Long)"가
   * 들어있다(SecurityContext에 세팅됨). 즉 이 메서드가 호출되는 시점엔 이미 인증이 끝난 상태이고,
   * principal을 DB에서 실제 User row로 한 번 더 조회해 최신 정보(닉네임 등)를 가져오는 것.
   */
  private User currentUser(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return userRepository.findById(userId).orElse(null);
  }

  /**
   * 이 유저가 이미 "탈퇴하지 않은(활성)" 커플 멤버십을 갖고 있는지 확인한다.
   * CoupleMemberRepository.findByUser_IdAndLeftAtIsNull은 Spring Data JPA의 쿼리 메서드 기능으로,
   * 메서드 이름만 보고 "WHERE user_id = ? AND left_at IS NULL" SQL을 자동 생성해준다.
   * left_at이 null이라는 것은 "아직 이 커플에서 나가지 않았다 = 지금 연결되어 있다"는 뜻이다.
   */
  private boolean isAlreadyLinked(Long userId) {
    return coupleMemberRepository.findByUser_IdAndLeftAtIsNull(userId).isPresent();
  }

  // ── 1) 초대 링크 생성 ───────────────────────────────────────────────────

  /**
   * POST /api/couple/invite
   * 로그인한 사용자가 새 초대 토큰(CoupleInvite, status=PENDING)을 만든다.
   * 이미 커플이 연결되어 있으면 또 초대를 만들 이유가 없으므로 409(Conflict)로 막는다.
   */
  @PostMapping("/invite")
  public ResponseEntity<?> createInvite(Authentication authentication) {
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }
    if (isAlreadyLinked(me.getId())) {
      return ResponseEntity.status(409).body(Map.of("error", "이미 연결된 커플이 있습니다"));
    }

    // UUID.randomUUID(): 충돌 가능성이 사실상 0에 가까운 128비트 무작위 값(예: 3fa85f64-5717-...).
    // 이걸 토큰으로 쓰면 "이 값을 아는 사람만 초대를 수락할 수 있다"는 보안성이 생긴다
    // (순차 증가하는 숫자 ID를 토큰으로 쓰면 남이 값을 추측해서 접근할 위험이 있음).
    String token = UUID.randomUUID().toString();
    LocalDateTime expiresAt = LocalDateTime.now().plusHours(INVITE_EXPIRES_HOURS);

    // Builder 패턴: CoupleInvite 엔티티는 필드를 하나하나 설정자(setter)로 채우는 대신,
    // .builder().필드(값)...build() 형태의 체이닝으로 불변 객체를 한 번에 만든다(Lombok @Builder).
    CoupleInvite invite = CoupleInvite.builder()
        .inviter(me)
        .token(token)
        .status("PENDING")
        .expiresAt(expiresAt)
        .createdAt(LocalDateTime.now())
        .build();
    coupleInviteRepository.save(invite); // INSERT 실행 (JPA가 SQL을 자동 생성)

    String inviteUrl = frontendBaseUrl + "/couple/connect/" + token;
    return ResponseEntity.ok(new InviteResponse(token, inviteUrl, expiresAt));
  }

  // ── 2) 초대 수락 (실제 커플 생성이 일어나는 지점) ─────────────────────────

  /**
   * POST /api/couple/invite/{token}/accept
   * 상대방(B)이 초대 링크를 열고 로그인한 상태에서 이 API를 호출하면, 검증을 통과했을 때
   * 비로소 Couple 1건 + CoupleMember 2건(A, B)이 실제로 생성된다.
   *
   * <p>검증 순서가 중요하다 — 앞의 체크를 통과해야 뒤의 체크로 넘어가고, 하나라도 걸리면
   * 그 즉시 에러를 반환하고 아무 것도 생성하지 않는다(원자적으로 전부 성공하거나 전부 실패):
   * <ol>
   *   <li>토큰이 실제로 존재하는가</li>
   *   <li>그 초대가 아직 PENDING 상태인가 (이미 ACCEPTED/EXPIRED면 재사용 불가)</li>
   *   <li>만료 시각이 지나지 않았는가 (지났으면 이 시점에 status를 EXPIRED로 갱신해둔다)</li>
   *   <li>자기 자신이 만든 링크를 자기가 수락하려는 건 아닌가</li>
   *   <li>초대한 사람과 성별이 같지는 않은가 (동성 커플 연결 방지)</li>
   *   <li>내가 이미 다른 커플에 연결되어 있진 않은가</li>
   *   <li>초대를 만든 사람(A)이 그 사이에 이미 다른 커플에 연결되어버리진 않았는가</li>
   * </ol>
   */
  @PostMapping("/invite/{token}/accept")
  public ResponseEntity<?> acceptInvite(Authentication authentication, @PathVariable String token) {
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }

    Optional<CoupleInvite> inviteOpt = coupleInviteRepository.findByToken(token);
    if (inviteOpt.isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "유효하지 않은 초대 링크입니다"));
    }
    CoupleInvite invite = inviteOpt.get();

    if (!"PENDING".equals(invite.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "이미 사용되었거나 취소된 초대 링크입니다"));
    }
    if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
      // 만료된 걸 확인한 김에 상태도 EXPIRED로 갱신해서, 다음에 같은 토큰이 또 들어와도
      // 매번 "PENDING인데 시간만 지남" 상태로 애매하게 남지 않도록 정리해둔다.
      invite.setStatus("EXPIRED");
      coupleInviteRepository.save(invite);
      return ResponseEntity.status(410).body(Map.of("error", "만료된 초대 링크입니다"));
    }
    if (invite.getInviter().getId().equals(me.getId())) {
      return ResponseEntity.badRequest().body(Map.of("error", "본인이 만든 초대 링크는 수락할 수 없습니다"));
    }
    if (invite.getInviter().getGender().equals(me.getGender())) {
      return ResponseEntity.badRequest().body(Map.of("error", "이성하고만 연결할 수 있습니다"));
    }
    if (isAlreadyLinked(me.getId())) {
      return ResponseEntity.status(409).body(Map.of("error", "이미 연결된 커플이 있습니다"));
    }
    if (isAlreadyLinked(invite.getInviter().getId())) {
      // A가 초대 링크를 여러 개 만들어뒀다가, 그중 하나가 먼저 수락되어 이미 커플이 된 경우를 방지.
      return ResponseEntity.status(409).body(Map.of("error", "상대방이 이미 다른 커플과 연결되어 있습니다"));
    }

    // 여기까지 오면 모든 검증 통과 -> 실제 매칭 데이터 생성.
    // Couple.builder().build()처럼 필드를 하나도 안 채우고 저장하는 이유:
    // Couple 엔티티의 status/connected_at은 DB 컬럼 자체에 DEFAULT 값(ACTIVE, SYSTIMESTAMP)이
    // 걸려 있고 엔티티에서도 insertable=false로 막아뒀기 때문에, INSERT 문에 아예 안 실려가고
    // DB가 알아서 기본값을 채워준다(다른 도메인의 PlaceStyle 등과 같은 패턴).
    Couple couple = coupleRepository.save(Couple.builder().build());

    // 커플이 만들어진 뒤, 두 사람 각각을 couple_members에 한 행씩 연결한다.
    coupleMemberRepository.save(CoupleMember.builder().couple(couple).user(invite.getInviter()).build());
    coupleMemberRepository.save(CoupleMember.builder().couple(couple).user(me).build());

    // 초대 티켓 자체도 "누가, 언제, 어떤 커플로 이어졌는지" 이력을 남기기 위해 갱신해둔다
    // (나중에 "초대 수락 이력" 같은 기능을 만들 때 이 값들을 그대로 활용할 수 있음).
    invite.setStatus("ACCEPTED");
    invite.setAcceptedBy(me);
    invite.setCouple(couple);
    coupleInviteRepository.save(invite);

    return ResponseEntity.ok(Map.of("success", true, "coupleId", couple.getId()));
  }

  // ── 3) 연결 상태 조회 ───────────────────────────────────────────────────

  /**
   * GET /api/couple/status
   * 로그인한 유저가 현재 누군가와 연결되어 있는지, 연결되어 있다면 상대방이 누구인지 알려준다.
   * 홈/마이페이지/커플싱크 탭 등에서 "연결됨/미연결"에 따라 화면을 다르게 보여줄 때 사용.
   */
  @GetMapping("/status")
  public ResponseEntity<?> getStatus(Authentication authentication) {
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }

    // 1단계: 내가 활성 상태로 속한 couple_members 행이 있는지 확인
    Optional<CoupleMember> myMembership = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(me.getId());
    if (myMembership.isEmpty()) {
      // 연결된 적 없음 -> partner/connectedAt/metDate는 그냥 null로 응답
      return ResponseEntity.ok(new StatusResponse(false, null, null, null));
    }

    // 2단계: 내가 속한 Couple의 멤버 전체(나 + 상대방, 최대 2명)를 불러와서
    //        "나 자신이 아니면서 아직 나가지 않은(left_at == null)" 사람을 상대방으로 찾는다.
    Couple couple = myMembership.get().getCouple();
    List<CoupleMember> members = coupleMemberRepository.findByCoupleId(couple.getId());
    CoupleMember partnerMembership = members.stream()
        .filter(m -> m.getLeftAt() == null && !m.getUser().getId().equals(me.getId()))
        .findFirst()
        .orElse(null);

    PartnerDto partner = partnerMembership == null ? null : new PartnerDto(
        partnerMembership.getUser().getId(),
        partnerMembership.getUser().getNickname(),
        partnerMembership.getUser().getEmail(),
        partnerMembership.getUser().getGender(),
        partnerMembership.getUser().getProfileImageUrl()
    );

    return ResponseEntity.ok(new StatusResponse(true, partner, couple.getConnectedAt(), couple.getMetDate()));
  }

  // ── 3-1) 만난 날짜 등록/수정 ─────────────────────────────────────────────

  /**
   * PUT /api/couple/met-date
   * 커플로 연결된 두 사람 중 누구든 "실제로 처음 만난 날짜"를 등록/수정할 수 있다.
   * connectedAt(이 사이트에서 연결된 시각)은 Couple 생성 시점에 자동으로 정해지는 값이라 수정이
   * 불가능하지만, met_date는 그와 무관하게 사용자가 직접 입력하는 값이라 언제든 바꿀 수 있다.
   */
  @PutMapping("/met-date")
  public ResponseEntity<?> updateMetDate(Authentication authentication, @RequestBody MetDateRequest req) {
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }

    Optional<CoupleMember> myMembership = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(me.getId());
    if (myMembership.isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "연결된 커플이 없습니다"));
    }
    if (req.metDate() != null && req.metDate().isAfter(LocalDate.now())) {
      return ResponseEntity.badRequest().body(Map.of("error", "미래 날짜는 입력할 수 없습니다"));
    }

    Couple couple = myMembership.get().getCouple();
    couple.setMetDate(req.metDate());
    coupleRepository.save(couple);

    return ResponseEntity.ok(Map.of("success", true, "metDate", req.metDate() == null ? "" : req.metDate().toString()));
  }

  // ── 4) 연결 해제 ───────────────────────────────────────────────────────

  /**
   * DELETE /api/couple
   * 커플 관계를 끊는다. couple_members 행을 실제로 삭제하지 않고 left_at에 현재 시각을 기록하는
   * 방식(소프트 삭제)을 쓰는데, 이렇게 하면 "예전에 누구와 언제부터 언제까지 연결되어 있었는지"
   * 이력이 DB에 그대로 남아서 나중에 통계나 복구 기능을 만들 때 유용하다.
   */
  @DeleteMapping
  public ResponseEntity<?> disconnect(Authentication authentication) {
    User me = currentUser(authentication);
    if (me == null) {
      return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
    }

    Optional<CoupleMember> myMembership = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(me.getId());
    if (myMembership.isEmpty()) {
      return ResponseEntity.status(404).body(Map.of("error", "연결된 커플이 없습니다"));
    }

    Couple couple = myMembership.get().getCouple();
    LocalDateTime now = LocalDateTime.now();
    // 나뿐 아니라 상대방의 멤버십에도 left_at을 기록해야 두 사람 모두 "미연결" 상태가 된다.
    // (내 것만 지우면 상대방 화면에서는 여전히 "연결됨"으로 보이는 반쪽짜리 해제가 되어버림)
    for (CoupleMember member : coupleMemberRepository.findByCoupleId(couple.getId())) {
      if (member.getLeftAt() == null) {
        member.setLeftAt(now);
        coupleMemberRepository.save(member);
      }
    }
    couple.setStatus("DISCONNECTED");
    coupleRepository.save(couple);

    return ResponseEntity.ok(Map.of("success", true));
  }
}

package org.ict.datemanagerbackend.domain.couple.service;

import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.couple.dto.CoupleInviteResponse;
import org.ict.datemanagerbackend.domain.couple.dto.CouplePartnerDto;
import org.ict.datemanagerbackend.domain.couple.dto.CoupleStatusResponse;
import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleInvite;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleMember;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleInviteRepository;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoupleServiceImpl implements CoupleService {

  // 초대 링크의 유효 기간. 프론트(CoupleTab.jsx)의 "링크는 1시간 동안 유효합니다" 문구와 반드시 맞춰야 함.
  private static final long INVITE_EXPIRES_HOURS = 1;

  private final CoupleRepository coupleRepository;
  private final CoupleMemberRepository coupleMemberRepository;
  private final CoupleInviteRepository coupleInviteRepository;

  // application.yaml의 app.frontend-base-url을 그대로 주입받아, 초대 토큰을 실제 클릭 가능한
  // URL(프론트 주소 + /couple/connect/{token})로 조립하는 데 사용한다.
  @Value("${app.frontend-base-url}")
  private String frontendBaseUrl;

  /**
   * 이 유저가 이미 "탈퇴하지 않은(활성)" 커플 멤버십을 갖고 있는지 확인한다.
   * left_at이 null이라는 것은 "아직 이 커플에서 나가지 않았다 = 지금 연결되어 있다"는 뜻이다.
   */
  private boolean isAlreadyLinked(Long userId) {
    return coupleMemberRepository.findByUser_IdAndLeftAtIsNull(userId).isPresent();
  }

  @Override
  public CoupleInviteResponse createInvite(User me) {
    if (isAlreadyLinked(me.getId())) {
      throw new CoupleActionException(409, "이미 연결된 커플이 있습니다");
    }

    // UUID.randomUUID(): 충돌 가능성이 사실상 0에 가까운 128비트 무작위 값. 이걸 토큰으로 쓰면
    // "이 값을 아는 사람만 초대를 수락할 수 있다"는 보안성이 생긴다(순차 증가하는 숫자 ID를
    // 토큰으로 쓰면 남이 값을 추측해서 접근할 위험이 있음).
    String token = UUID.randomUUID().toString();
    LocalDateTime expiresAt = LocalDateTime.now().plusHours(INVITE_EXPIRES_HOURS);

    CoupleInvite invite = CoupleInvite.builder()
        .inviter(me)
        .token(token)
        .status("PENDING")
        .expiresAt(expiresAt)
        .createdAt(LocalDateTime.now())
        .build();
    coupleInviteRepository.save(invite);

    String inviteUrl = frontendBaseUrl + "/couple/connect/" + token;
    return new CoupleInviteResponse(token, inviteUrl, expiresAt);
  }

  /**
   * 검증 순서가 중요하다 — 앞의 체크를 통과해야 뒤의 체크로 넘어가고, 하나라도 걸리면
   * 그 즉시 예외를 던지고 아무 것도 생성하지 않는다(원자적으로 전부 성공하거나 전부 실패):
   * 1. 토큰이 실제로 존재하는가
   * 2. 그 초대가 아직 PENDING 상태인가 (이미 ACCEPTED/EXPIRED면 재사용 불가)
   * 3. 만료 시각이 지나지 않았는가 (지났으면 이 시점에 status를 EXPIRED로 갱신해둔다)
   * 4. 자기 자신이 만든 링크를 자기가 수락하려는 건 아닌가
   * 5. 초대한 사람과 성별이 같지는 않은가 (동성 커플 연결 방지)
   * 6. 내가 이미 다른 커플에 연결되어 있진 않은가
   * 7. 초대를 만든 사람(A)이 그 사이에 이미 다른 커플에 연결되어버리진 않았는가
   */
  @Override
  public Long acceptInvite(User me, String token) {
    Optional<CoupleInvite> inviteOpt = coupleInviteRepository.findByToken(token);
    if (inviteOpt.isEmpty()) {
      throw new CoupleActionException(404, "유효하지 않은 초대 링크입니다");
    }
    CoupleInvite invite = inviteOpt.get();

    if (!"PENDING".equals(invite.getStatus())) {
      throw new CoupleActionException(409, "이미 사용되었거나 취소된 초대 링크입니다");
    }
    if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
      // 만료된 걸 확인한 김에 상태도 EXPIRED로 갱신해서, 다음에 같은 토큰이 또 들어와도
      // 매번 "PENDING인데 시간만 지남" 상태로 애매하게 남지 않도록 정리해둔다.
      invite.setStatus("EXPIRED");
      coupleInviteRepository.save(invite);
      throw new CoupleActionException(410, "만료된 초대 링크입니다");
    }
    if (invite.getInviter().getId().equals(me.getId())) {
      throw new CoupleActionException(400, "본인이 만든 초대 링크는 수락할 수 없습니다");
    }
    if (invite.getInviter().getGender().equals(me.getGender())) {
      throw new CoupleActionException(400, "이성하고만 연결할 수 있습니다");
    }
    if (isAlreadyLinked(me.getId())) {
      throw new CoupleActionException(409, "이미 연결된 커플이 있습니다");
    }
    if (isAlreadyLinked(invite.getInviter().getId())) {
      // A가 초대 링크를 여러 개 만들어뒀다가, 그중 하나가 먼저 수락되어 이미 커플이 된 경우를 방지.
      throw new CoupleActionException(409, "상대방이 이미 다른 커플과 연결되어 있습니다");
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

    return couple.getId();
  }

  @Override
  public CoupleStatusResponse getStatus(User me) {
    // 1단계: 내가 활성 상태로 속한 couple_members 행이 있는지 확인
    Optional<CoupleMember> myMembership = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(me.getId());
    if (myMembership.isEmpty()) {
      // 연결된 적 없음 -> partner/connectedAt/metDate는 그냥 null로 응답
      return new CoupleStatusResponse(false, null, null, null);
    }

    // 2단계: 내가 속한 Couple의 멤버 전체(나 + 상대방, 최대 2명)를 불러와서
    //        "나 자신이 아니면서 아직 나가지 않은(left_at == null)" 사람을 상대방으로 찾는다.
    Couple couple = myMembership.get().getCouple();
    List<CoupleMember> members = coupleMemberRepository.findByCoupleId(couple.getId());
    CoupleMember partnerMembership = members.stream()
        .filter(m -> m.getLeftAt() == null && !m.getUser().getId().equals(me.getId()))
        .findFirst()
        .orElse(null);

    CouplePartnerDto partner = partnerMembership == null ? null : new CouplePartnerDto(
        partnerMembership.getUser().getId(),
        partnerMembership.getUser().getNickname(),
        partnerMembership.getUser().getEmail(),
        partnerMembership.getUser().getGender(),
        partnerMembership.getUser().getProfileImageUrl()
    );

    return new CoupleStatusResponse(true, partner, couple.getConnectedAt(), couple.getMetDate());
  }

  /**
   * 커플로 연결된 두 사람 중 누구든 "실제로 처음 만난 날짜"를 등록/수정할 수 있다.
   * connectedAt(이 사이트에서 연결된 시각)은 Couple 생성 시점에 자동으로 정해지는 값이라 수정이
   * 불가능하지만, met_date는 그와 무관하게 사용자가 직접 입력하는 값이라 언제든 바꿀 수 있다.
   */
  @Override
  public void updateMetDate(User me, LocalDate metDate) {
    Optional<CoupleMember> myMembership = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(me.getId());
    if (myMembership.isEmpty()) {
      throw new CoupleActionException(404, "연결된 커플이 없습니다");
    }
    if (metDate != null && metDate.isAfter(LocalDate.now())) {
      throw new CoupleActionException(400, "미래 날짜는 입력할 수 없습니다");
    }

    Couple couple = myMembership.get().getCouple();
    couple.setMetDate(metDate);
    coupleRepository.save(couple);
  }

  /**
   * 커플 관계를 끊는다. couple_members 행을 실제로 삭제하지 않고 left_at에 현재 시각을 기록하는
   * 방식(소프트 삭제)을 쓰는데, 이렇게 하면 "예전에 누구와 언제부터 언제까지 연결되어 있었는지"
   * 이력이 DB에 그대로 남아서 나중에 통계나 복구 기능을 만들 때 유용하다.
   */
  @Override
  public void disconnect(User me) {
    Optional<CoupleMember> myMembership = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(me.getId());
    if (myMembership.isEmpty()) {
      throw new CoupleActionException(404, "연결된 커플이 없습니다");
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
  }
}

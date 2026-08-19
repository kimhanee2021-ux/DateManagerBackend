package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateCoupleRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminCoupleDto;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminCoupleMemberDto;
import org.ict.datemanagerbackend.domain.couple.entity.Couple;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleMember;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleRepository;
import org.ict.datemanagerbackend.domain.subscription.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class AdminCoupleServiceImpl implements AdminCoupleService {

  private static final Set<String> VALID_COUPLE_STATUSES = Set.of("ACTIVE", "DISCONNECTED");

  private final CoupleRepository coupleRepository;
  private final CoupleMemberRepository coupleMemberRepository;
  private final SubscriptionRepository subscriptionRepository;

  public AdminCoupleServiceImpl(CoupleRepository coupleRepository, CoupleMemberRepository coupleMemberRepository,
                                 SubscriptionRepository subscriptionRepository) {
    this.coupleRepository = coupleRepository;
    this.coupleMemberRepository = coupleMemberRepository;
    this.subscriptionRepository = subscriptionRepository;
  }

  @Override
  public List<AdminCoupleDto> listCouples(String filter, String status) {
    return coupleRepository.findAll().stream()
        .filter(c -> "all".equals(status) || "ACTIVE".equals(c.getStatus()))
        .map(this::toDto)
        .filter(dto -> matchesFilter(dto, filter))
        .toList();
  }

  @Override
  public AdminCoupleDto updateCouple(Long id, AdminUpdateCoupleRequest request) {
    if (request.status() == null || !VALID_COUPLE_STATUSES.contains(request.status())) {
      throw new IllegalArgumentException("status는 ACTIVE 또는 DISCONNECTED 여야 합니다");
    }
    Couple couple = coupleRepository.findById(id).orElseThrow(() -> new NoSuchElementException("커플을 찾을 수 없습니다"));
    boolean reactivating = "ACTIVE".equals(request.status());
    LocalDateTime now = LocalDateTime.now();
    couple.setStatus(request.status());
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

    return toDto(couple);
  }

  @Override
  public void deleteCouple(Long id) {
    if (!coupleRepository.existsById(id)) {
      throw new NoSuchElementException("커플을 찾을 수 없습니다");
    }
    // 커플 기념일/AI채팅/코스 등 연결된 데이터가 있으면 FK 제약으로 삭제가 막혀
    // DataIntegrityViolationException이 그대로 던져진다 - 컨트롤러가 409로 매핑.
    List<CoupleMember> members = coupleMemberRepository.findByCoupleId(id);
    coupleMemberRepository.deleteAll(members);
    coupleRepository.deleteById(id);
  }

  private boolean matchesFilter(AdminCoupleDto dto, String filter) {
    boolean anySubscribed = dto.members().stream().anyMatch(AdminCoupleMemberDto::subscribed);
    return switch (filter) {
      case "subscribed" -> anySubscribed;
      case "free" -> !anySubscribed;
      default -> true;
    };
  }

  private AdminCoupleDto toDto(Couple couple) {
    List<AdminCoupleMemberDto> members = coupleMemberRepository.findByCoupleId(couple.getId()).stream()
        .map(cm -> new AdminCoupleMemberDto(cm.getUser().getId(), cm.getUser().getNickname(),
            cm.getUser().getEmail(), cm.getRoleType(),
            subscriptionRepository.existsByUserIdAndStatus(cm.getUser().getId(), "ACTIVE")))
        .toList();
    return new AdminCoupleDto(couple.getId(), couple.getStatus(), couple.getConnectedAt(), members);
  }
}

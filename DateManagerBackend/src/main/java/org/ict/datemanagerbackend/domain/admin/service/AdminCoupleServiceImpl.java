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
  public List<AdminCoupleDto> listCouples(String filter) {
    return coupleRepository.findAll().stream()
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
    couple.setStatus(request.status());
    coupleRepository.save(couple);
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

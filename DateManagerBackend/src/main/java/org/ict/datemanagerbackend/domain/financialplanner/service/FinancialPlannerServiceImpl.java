package org.ict.datemanagerbackend.domain.financialplanner.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.datemanagerbackend.domain.aichat.dto.FundGoalParseDto;
import org.ict.datemanagerbackend.domain.aichat.service.AiChatService;
import org.ict.datemanagerbackend.domain.couple.entity.CoupleMember;
import org.ict.datemanagerbackend.domain.couple.repository.CoupleMemberRepository;
import org.ict.datemanagerbackend.domain.financialplanner.dto.DashboardResponseDto;
import org.ict.datemanagerbackend.domain.financialplanner.dto.ExchangeBriefingDto;
import org.ict.datemanagerbackend.domain.financialplanner.dto.GoalResultDto;
import org.ict.datemanagerbackend.domain.financialplanner.dto.RecommendedProductDto;
import org.ict.datemanagerbackend.domain.financialplanner.entity.CachedExchangeRate;
import org.ict.datemanagerbackend.domain.financialplanner.entity.CachedFinancialProduct;
import org.ict.datemanagerbackend.domain.financialplanner.entity.FundGoal;
import org.ict.datemanagerbackend.domain.financialplanner.entity.OwnerType;
import org.ict.datemanagerbackend.domain.financialplanner.repository.CachedExchangeRateRepository;
import org.ict.datemanagerbackend.domain.financialplanner.repository.CachedFinancialProductRepository;
import org.ict.datemanagerbackend.domain.financialplanner.repository.FundGoalRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// AI 스마트 자금 플래너 핵심 서비스(2026-08-31). "owner당 진행 중인 목표 1개" 원칙으로 생성=수정을
// 같은 메서드에서 upsert 형태로 처리한다(FundGoal 주석 참고).
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialPlannerServiceImpl implements FinancialPlannerService {

  // finlife API는 상품 개별 딥링크를 안 줘서(2026-08-31 실측 확인), "가입하러 가기"는 이 비교공시
  // 사이트로 통일해서 연결한다.
  private static final String FINLIFE_COMPARISON_URL = "https://finlife.fss.or.kr/finlife/main/main.do";

  private final FundGoalRepository fundGoalRepository;
  private final CachedFinancialProductRepository cachedFinancialProductRepository;
  private final CachedExchangeRateRepository cachedExchangeRateRepository;
  private final CoupleMemberRepository coupleMemberRepository;
  private final AiChatService aiChatService;

  // 로그인 유저가 현재 커플로 연결돼 있으면 목표를 커플 공동으로, 아니면 개인으로 자동 귀속시킨다
  // (원본 기획 의도인 "수동 분기 없는 자동 처리").
  private Owner resolveOwner(User user) {
    Optional<CoupleMember> membership = coupleMemberRepository.findByUser_IdAndLeftAtIsNull(user.getId());
    if (membership.isPresent()) {
      return new Owner(OwnerType.COUPLE, membership.get().getCouple().getId());
    }
    return new Owner(OwnerType.SOLO, user.getId());
  }

  private record Owner(OwnerType type, Long id) {
  }

  @Override
  public GoalResultDto createOrUpdateGoal(User user, String text) {
    Owner owner = resolveOwner(user);
    FundGoalParseDto parsed = aiChatService.parseFundGoal(text);

    if (parsed.needsClarification()) {
      return new GoalResultDto(true, parsed.question(), null, null, null, null, null, null, null, owner.type().name());
    }

    FundGoal goal = fundGoalRepository.findByOwnerTypeAndOwnerId(owner.type(), owner.id())
        .orElseGet(() -> FundGoal.builder()
            .ownerType(owner.type())
            .ownerId(owner.id())
            .currentAmount(0L)
            .build());

    goal.setTitle(parsed.title());
    goal.setTargetAmount(parsed.targetAmount());
    goal.setTargetPeriodMonth(parsed.targetPeriodMonth());
    goal.setCategory(parsed.category());
    goal.setDestinationCountry(parsed.destinationCountry());
    // 목표 내용이 바뀌었으니 캐시된 AI 코멘트는 다음 대시보드 조회 때 새로 생성되게 비운다.
    goal.setAiComment(null);
    goal.setAiCommentGeneratedAt(null);
    goal.setUpdatedAt(LocalDateTime.now());

    FundGoal saved = fundGoalRepository.save(goal);
    return toResultDto(saved, owner.type());
  }

  @Override
  public DashboardResponseDto getDashboard(User user) {
    Owner owner = resolveOwner(user);
    Optional<FundGoal> goalOpt = fundGoalRepository.findByOwnerTypeAndOwnerId(owner.type(), owner.id());
    if (goalOpt.isEmpty()) {
      return new DashboardResponseDto(null, null, null, List.of());
    }
    FundGoal goal = goalOpt.get();

    String comment = resolveComment(goal);

    String currency = DestinationCurrencyMapper.resolveCurrency(goal.getDestinationCountry());
    ExchangeBriefingDto exchangeBriefing = currency == null ? null
        : cachedExchangeRateRepository.findById(currency)
            .map(r -> new ExchangeBriefingDto(r.getCurrencyCode(), r.getRate(), r.getPrevRate(), r.getFetchedAt()))
            .orElse(null);

    List<RecommendedProductDto> products = goal.getTargetPeriodMonth() == null ? List.of()
        : cachedFinancialProductRepository
            .findBySaveTrmOrderByIntrRate2Desc(closestSaveTrm(goal.getTargetPeriodMonth()), PageRequest.of(0, 5))
            .stream()
            .map(this::toProductDto)
            .toList();

    return new DashboardResponseDto(toResultDto(goal, owner.type()), comment, exchangeBriefing, products);
  }

  // finlife 상품은 예치기간이 1/3/6/12/24/36개월처럼 정해진 값만 있어서(2026-08-31 실측), 목표
  // 기간(예: 10개월)이 정확히 안 맞으면 추천 상품이 0건이 되던 문제를 고쳤다 - 실제 존재하는
  // 예치기간 중 목표 기간과 가장 가까운 값을 찾아 그걸로 조회한다.
  private Integer closestSaveTrm(Integer targetPeriodMonth) {
    List<Integer> available = cachedFinancialProductRepository.findDistinctSaveTrms();
    if (available.isEmpty()) return targetPeriodMonth;
    return available.stream()
        .min(java.util.Comparator.comparingInt(trm -> Math.abs(trm - targetPeriodMonth)))
        .orElse(targetPeriodMonth);
  }

  // 목표 데이터가 그대로면 하루 1번만 재생성(비용 절감, 개발 명세서 3-4 보완사항).
  private String resolveComment(FundGoal goal) {
    if (goal.getAiComment() != null && goal.getAiCommentGeneratedAt() != null
        && goal.getAiCommentGeneratedAt().isAfter(LocalDateTime.now().minus(24, ChronoUnit.HOURS))) {
      return goal.getAiComment();
    }
    String comment = aiChatService.generateFundGoalComment(
        goal.getTitle(), goal.getTargetAmount(), goal.getCurrentAmount(), goal.getTargetPeriodMonth());
    goal.setAiComment(comment);
    goal.setAiCommentGeneratedAt(LocalDateTime.now());
    fundGoalRepository.save(goal);
    return comment;
  }

  @Override
  public GoalResultDto updateProgress(User user, Long goalId, Long currentAmount) {
    Owner owner = resolveOwner(user);
    FundGoal goal = fundGoalRepository.findById(goalId)
        .orElseThrow(() -> new IllegalArgumentException("목표를 찾을 수 없습니다"));
    if (goal.getOwnerType() != owner.type() || !goal.getOwnerId().equals(owner.id())) {
      throw new IllegalArgumentException("본인(또는 커플) 목표만 수정할 수 있습니다");
    }
    goal.setCurrentAmount(currentAmount);
    goal.setUpdatedAt(LocalDateTime.now());
    FundGoal saved = fundGoalRepository.save(goal);
    return toResultDto(saved, owner.type());
  }

  private GoalResultDto toResultDto(FundGoal goal, OwnerType ownerType) {
    return new GoalResultDto(false, null, goal.getId(), goal.getTitle(), goal.getTargetAmount(),
        goal.getCurrentAmount(), goal.getTargetPeriodMonth(), goal.getCategory(),
        goal.getDestinationCountry(), ownerType.name());
  }

  private RecommendedProductDto toProductDto(CachedFinancialProduct p) {
    return new RecommendedProductDto(p.getBankName(), p.getProductName(), p.getProductType().name(),
        p.getSaveTrm(), p.getIntrRate(), p.getIntrRate2(), FINLIFE_COMPARISON_URL);
  }
}

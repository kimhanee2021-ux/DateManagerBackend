package org.ict.datemanagerbackend.domain.financialplanner.repository;

import org.ict.datemanagerbackend.domain.financialplanner.entity.FundGoal;
import org.ict.datemanagerbackend.domain.financialplanner.entity.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FundGoalRepository extends JpaRepository<FundGoal, Long> {

  // (ownerType, ownerId)당 진행 중인 목표는 항상 1개뿐이라는 전제(2026-08-31 결정)로 조회한다.
  Optional<FundGoal> findByOwnerTypeAndOwnerId(OwnerType ownerType, Long ownerId);
}

package org.ict.datemanagerbackend.domain.financialplanner.repository;

import org.ict.datemanagerbackend.domain.financialplanner.entity.CachedExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CachedExchangeRateRepository extends JpaRepository<CachedExchangeRate, String> {
}

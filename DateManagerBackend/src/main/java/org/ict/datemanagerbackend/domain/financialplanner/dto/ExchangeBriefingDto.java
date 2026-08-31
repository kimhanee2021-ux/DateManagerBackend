package org.ict.datemanagerbackend.domain.financialplanner.dto;

import java.time.LocalDateTime;

public record ExchangeBriefingDto(
    String currencyCode,
    Double rate,
    Double prevRate,
    LocalDateTime fetchedAt
) {
}

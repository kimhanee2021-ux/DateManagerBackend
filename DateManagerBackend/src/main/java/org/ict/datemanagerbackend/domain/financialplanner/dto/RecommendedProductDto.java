package org.ict.datemanagerbackend.domain.financialplanner.dto;

public record RecommendedProductDto(
    String bankName,
    String productName,
    String productType, // "DEPOSIT" | "SAVING"
    Integer saveTrm,
    Double intrRate,
    Double intrRate2,
    String linkUrl
) {
}

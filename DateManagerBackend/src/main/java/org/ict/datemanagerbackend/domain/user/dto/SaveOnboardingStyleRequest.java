package org.ict.datemanagerbackend.domain.user.dto;

// 프론트의 energetic 축은 API 호출 직전에 energy로 변환해서 전달한다.
// API와 DB에서는 UserStyle.initEnergy와 같은 energy 명칭으로 통일한다.
public record SaveOnboardingStyleRequest(
    Integer energy,
    Integer immersion,
    Integer vibe,
    Integer aesthetic,
    Integer pacing,
    Integer depth
) {
}

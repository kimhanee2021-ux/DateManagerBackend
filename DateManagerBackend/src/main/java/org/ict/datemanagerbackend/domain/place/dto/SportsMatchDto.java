package org.ict.datemanagerbackend.domain.place.dto;

// 서울올림픽기념국민체육진흥공단_체육진흥투표권(스포츠toto,프로토)발매대상 경기정보_GW API
// (todz_api_tb_match_mgmt_i) 응답 중 필요한 필드만 담음. 필드명은 실제 응답 JSON 키를 그대로 옮김
// (2026-08-20, 실제 호출로 확인).
public record SportsMatchDto(
    String matchYmd,       // match_ymd - 경기일자 (yyyyMMdd)
    String matchTm,        // match_tm - 경기시각 (HHmm)
    String sportName,      // match_sport_han_nm - 종목명 (야구/축구/농구/배구)
    String leagueName,     // leag_han_nm - 리그명 (KBO, K리그1 등)
    String homeTeam,       // hteam_han_nm - 홈팀명
    String awayTeam,       // ateam_han_nm - 원정팀명
    String stadiumName,    // stdm_han_nm - 경기장명 (좌표 없이 이름만 내려옴)
    String matchResult      // match_end_val - 경기결과(스코어). 비어있으면 아직 안 열린(예정) 경기
) {
}

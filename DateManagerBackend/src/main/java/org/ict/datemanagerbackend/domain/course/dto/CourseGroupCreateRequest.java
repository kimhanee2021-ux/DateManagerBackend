package org.ict.datemanagerbackend.domain.course.dto;

// userId는 요청 body가 아니라 인증된 토큰(Authentication)에서 가져온다 - 예전엔 이 값을
// body로 그대로 받아서 아무 유저 id나 넣으면 남의 이름으로 코스그룹이 만들어지는 문제가 있었다.
public record CourseGroupCreateRequest(String title, Long coupleId) {
}

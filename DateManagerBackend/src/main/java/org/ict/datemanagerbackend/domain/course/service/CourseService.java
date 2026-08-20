package org.ict.datemanagerbackend.domain.course.service;

import org.ict.datemanagerbackend.domain.course.dto.CourseGroupResponseDto;
import org.ict.datemanagerbackend.domain.course.dto.CourseMatchSuggestionDto;
import org.ict.datemanagerbackend.domain.user.entity.User;

import java.util.List;

public interface CourseService {
  // <<코스 그룹 새로 만들기(빈 코스). 커플이면 couple_id도 같이 걸어줌>>
  CourseGroupResponseDto createGroup(User user, String title);
  // <<내가 만든 코스 그룹 목록(최신순)>>
  List<CourseGroupResponseDto> listGroups(User user);
  // <<코스 그룹 상세(담긴 장소 타임라인 포함) - 소유자 아니면 예외>>
  CourseGroupResponseDto getGroup(User user, Long courseGroupId);
  // <<코스 맨 뒤에 실제 place를 추가(=아이디어안의 "거점 고정"/이후 스팟 담기 둘 다 이 메서드)>>
  CourseGroupResponseDto addItem(User user, Long courseGroupId, Long placeId);
  // <<Step 2 자동 연결: 코스의 마지막 장소를 기준으로 20분 이내 + 성향(에너지) 보완 스팟 추천.
  // 추천만 하고 코스에 바로 담지는 않음(담으려면 addItem을 다시 호출)>>
  List<CourseMatchSuggestionDto> autoMatch(User user, Long courseGroupId);
  // <<코스 그룹 통째로 삭제(담긴 장소까지 전부) - 소유자 아니면 예외>>
  void deleteGroup(User user, Long courseGroupId);
  // <<코스에 담긴 장소 하나만 빼기 - 뺀 뒤 남은 장소들의 순서(sequence)를 빈틈없이 다시 매김>>
  CourseGroupResponseDto removeItem(User user, Long courseGroupId, Long itemId);
  // <<담긴 장소 순서를 한 칸 앞/뒤로 옮김("up"/"down") - 맨 앞에서 up, 맨 뒤에서 down이면 예외>>
  CourseGroupResponseDto moveItem(User user, Long courseGroupId, Long itemId, String direction);
}

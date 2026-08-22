package org.ict.datemanagerbackend.domain.board.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class BoardPostResponse {
  Long id;
  Long authorId;
  String authorNickname;
  String title;
  String content;
  int viewCount;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}

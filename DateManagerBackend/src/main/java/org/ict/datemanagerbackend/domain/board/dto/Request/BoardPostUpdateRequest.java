package org.ict.datemanagerbackend.domain.board.dto.Request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class BoardPostUpdateRequest {
  String title;
  String content;
}

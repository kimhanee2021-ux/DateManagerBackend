package org.ict.datemanagerbackend.domain.board.service;

import org.ict.datemanagerbackend.domain.board.dto.Request.BoardPostCreateRequest;
import org.ict.datemanagerbackend.domain.board.dto.Request.BoardPostUpdateRequest;
import org.ict.datemanagerbackend.domain.board.dto.Response.BoardPostResponse;

import java.util.List;

public interface BoardPostService {
  // <<글 등록>>
  BoardPostResponse createPost(Long userId, BoardPostCreateRequest request);
  // <<목록 조회>>
  List<BoardPostResponse> getAllPosts();
  // <<상세 조회 - 호출될 때마다 조회수 1 증가>>
  BoardPostResponse getPost(Long postId);
  // <<글 수정 - 작성자 본인만 가능>>
  BoardPostResponse updatePost(Long userId, Long postId, BoardPostUpdateRequest request);
  // <<글 삭제 - 작성자 본인만 가능>>
  void deletePost(Long userId, Long postId);
}

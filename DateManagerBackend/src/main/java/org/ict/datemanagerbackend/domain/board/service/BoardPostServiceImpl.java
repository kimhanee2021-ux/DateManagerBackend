package org.ict.datemanagerbackend.domain.board.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.datemanagerbackend.domain.board.dto.Request.BoardPostCreateRequest;
import org.ict.datemanagerbackend.domain.board.dto.Request.BoardPostUpdateRequest;
import org.ict.datemanagerbackend.domain.board.dto.Response.BoardPostResponse;
import org.ict.datemanagerbackend.domain.board.entity.BoardPost;
import org.ict.datemanagerbackend.domain.board.repository.BoardPostRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
@RequiredArgsConstructor
public class BoardPostServiceImpl implements BoardPostService {

  private final BoardPostRepository boardPostRepository;
  private final UserRepository userRepository;

  // 엔티티를 그대로 JSON으로 내려주지 않고 DTO로 한 번 감싸는 이유는 CoupleController의 PartnerDto와
  // 동일 - author 엔티티가 통째로 직렬화되는 걸 막고, 프론트가 실제로 쓰는 필드(작성자 닉네임)만 내려준다.
  private BoardPostResponse toResponse(BoardPost post) {
    return BoardPostResponse.builder()
        .id(post.getId())
        .authorId(post.getAuthor().getId())
        .authorNickname(post.getAuthor().getNickname())
        .title(post.getTitle())
        .content(post.getContent())
        .viewCount(post.getViewCount())
        .createdAt(post.getCreatedAt())
        .updatedAt(post.getUpdatedAt())
        .build();
  }

  @Override
  public BoardPostResponse createPost(Long userId, BoardPostCreateRequest request) {
    User author = userRepository.findById(userId)
        .orElseThrow(() -> new NoSuchElementException("사용자 아이디가 없습니다."));
    BoardPost post = BoardPost.builder()
        .author(author)
        .title(request.getTitle())
        .content(request.getContent())
        .build();
    return toResponse(boardPostRepository.save(post));
  }

  @Override
  public List<BoardPostResponse> getAllPosts() {
    return boardPostRepository.findAllByOrderByCreatedAtDesc().stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public BoardPostResponse getPost(Long postId) {
    BoardPost post = boardPostRepository.findById(postId)
        .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
    // 상세 화면을 열 때마다 조회수를 1 올린다. 동시에 여러 명이 같은 글을 열면 카운트가 살짝
    // 어긋날 수 있지만(동시성 문제), 예비용 백업 구현 범위에서는 단순 증가로 충분하다고 판단.
    post.setViewCount(post.getViewCount() + 1);
    return toResponse(boardPostRepository.save(post));
  }

  @Override
  public BoardPostResponse updatePost(Long userId, Long postId, BoardPostUpdateRequest request) {
    BoardPost post = boardPostRepository.findById(postId)
        .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
    if (!post.getAuthor().getId().equals(userId)) {
      throw new SecurityException("작성자만 수정할 수 있습니다.");
    }
    post.setTitle(request.getTitle());
    post.setContent(request.getContent());
    post.setUpdatedAt(LocalDateTime.now());
    return toResponse(boardPostRepository.save(post));
  }

  @Override
  public void deletePost(Long userId, Long postId) {
    BoardPost post = boardPostRepository.findById(postId)
        .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
    if (!post.getAuthor().getId().equals(userId)) {
      throw new SecurityException("작성자만 삭제할 수 있습니다.");
    }
    boardPostRepository.delete(post);
  }
}

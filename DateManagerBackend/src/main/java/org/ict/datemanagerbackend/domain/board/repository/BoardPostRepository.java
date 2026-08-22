package org.ict.datemanagerbackend.domain.board.repository;

import org.ict.datemanagerbackend.domain.board.entity.BoardPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    // 목록 화면 - 최신 글이 위로 오도록 정렬해서 전부 가져온다.
    List<BoardPost> findAllByOrderByCreatedAtDesc();
}

package org.ict.datemanagerbackend.domain.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ict.datemanagerbackend.domain.user.entity.User;

import java.time.LocalDateTime;

// 게시판(자유게시판) 글 하나. Notice(공지사항)와 달리 작성자가 관리자가 아니라 일반 회원 누구나이므로
// author 컬럼이 필요하다. 새로 만든 테이블이라 DB 레벨 DEFAULT가 없어서(Couple/Notice와 같은 이유)
// createdAt/updatedAt/viewCount는 애플리케이션이 직접 채운다.
@Entity
@Table(name = "board_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoardPost {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  @Setter
  @Column(nullable = false)
  private String title;

  @Setter
  @Column(nullable = false, length = 4000)
  private String content;

  @Setter
  @Builder.Default
  @Column(name = "view_count", nullable = false)
  private int viewCount = 0;

  @Builder.Default
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Setter
  @Builder.Default
  @Column(name = "updated_at")
  private LocalDateTime updatedAt = LocalDateTime.now();

}

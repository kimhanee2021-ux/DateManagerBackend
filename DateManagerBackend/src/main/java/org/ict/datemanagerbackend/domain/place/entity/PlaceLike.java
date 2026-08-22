package org.ict.datemanagerbackend.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.datemanagerbackend.domain.user.entity.User;

import java.time.LocalDateTime;

// 2026-08-22 - "찜(좋아요)" 목록을 실제로 저장하는 테이블. 예전엔 CurationTab.jsx의 로컬 state
// (likedItemIds)로만 관리되고 새로고침하면 사라졌는데, 이젠 여기 저장해서 다음 방문 때도
// 유지된다. 같은 유저가 같은 장소를 두 번 좋아요 누를 수 없도록 (user_id, place_id) 유니크 제약.
@Entity
@Table(name = "place_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "place_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PlaceLike {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "place_id", nullable = false)
  private Place place;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

}

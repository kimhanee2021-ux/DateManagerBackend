package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateUserRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminUserDto;
import org.ict.datemanagerbackend.domain.subscription.repository.SubscriptionRepository;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class AdminUserServiceImpl implements AdminUserService {

  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;

  public AdminUserServiceImpl(UserRepository userRepository, SubscriptionRepository subscriptionRepository) {
    this.userRepository = userRepository;
    this.subscriptionRepository = subscriptionRepository;
  }

  @Override
  public Page<AdminUserDto> listUsers(String search, String field, String filter, Pageable pageable) {
    Page<User> page = switch (filter) {
      case "subscribed" -> userRepository.searchActiveSubscribed(search, field, pageable);
      case "free" -> userRepository.searchActiveFree(search, field, pageable);
      default -> userRepository.searchActive(search, field, pageable);
    };
    return page.map(this::toDto);
  }

  @Override
  public AdminUserDto updateUser(Long id, AdminUpdateUserRequest request) {
    User user = userRepository.findById(id).orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다"));
    if (request.nickname() != null && !request.nickname().isBlank()) {
      user.setNickname(request.nickname());
    }
    if (request.gender() != null && !request.gender().isBlank()) {
      user.setGender(request.gender());
    }
    userRepository.save(user);
    return toDto(user);
  }

  // 실제 row를 지우지 않고 withdrawnAt만 채우는 탈퇴 처리(soft-delete). 탈퇴 후 1년 지난 계정의
  // 실제 삭제는 WithdrawnUserCleanupService가 배치로 처리한다.
  @Override
  public void withdrawUser(Long adminUserId, Long targetId) {
    if (adminUserId.equals(targetId)) {
      throw new IllegalArgumentException("관리자 본인 계정은 탈퇴 처리할 수 없습니다");
    }
    User target = userRepository.findById(targetId).orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다"));
    if (target.getWithdrawnAt() != null) {
      throw new IllegalArgumentException("이미 탈퇴 처리된 회원입니다");
    }
    target.setWithdrawnAt(LocalDateTime.now());
    userRepository.save(target);
  }

  private AdminUserDto toDto(User u) {
    boolean subscribed = subscriptionRepository.existsByUserIdAndStatus(u.getId(), "ACTIVE");
    return new AdminUserDto(u.getId(), u.getEmail(), u.getNickname(), u.getGender(), u.getCreatedAt(), subscribed);
  }
}

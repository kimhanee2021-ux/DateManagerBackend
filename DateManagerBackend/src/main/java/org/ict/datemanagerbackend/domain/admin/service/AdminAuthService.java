package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

// 관리자가 한 명뿐이라 별도 role 체계 없이, application.yaml의 app.admin-email과 로그인한 유저의
// 이메일이 같은지만 확인하는 방식으로 관리자 권한을 처리한다. 예전엔 AdminController와
// AdminSubscriptionController가 이 로직을 각자 복붙해서 갖고 있었는데(2026-08-18 발견), 모든
// Admin*Controller가 공유하도록 여기 하나로 모았다.
@Service
public class AdminAuthService {

  private final UserRepository userRepository;

  @Value("${app.admin-email}")
  private String adminEmail;

  public AdminAuthService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User currentUser(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return userRepository.findById(userId).orElse(null);
  }

  public boolean isAdmin(Authentication authentication) {
    return isAdmin(currentUser(authentication));
  }

  public boolean isAdmin(User user) {
    return user != null && user.getEmail() != null
        && !adminEmail.isBlank()
        && user.getEmail().equalsIgnoreCase(adminEmail);
  }
}

package org.ict.datemanagerbackend.domain.user.controller;

import org.ict.datemanagerbackend.domain.user.dto.ChangePasswordRequest;
import org.ict.datemanagerbackend.domain.user.dto.UpdateMeRequest;
import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.ict.datemanagerbackend.domain.user.service.ProfileImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileImageService profileImageService;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           ProfileImageService profileImageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.profileImageService = profileImageService;
    }

    @GetMapping
    public ResponseEntity<?> getMe(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
        }
        return ResponseEntity.ok(toResponse(userOpt.get()));
    }

    @PutMapping
    public ResponseEntity<?> updateMe(Authentication authentication, @RequestBody UpdateMeRequest req) {
        Long userId = (Long) authentication.getPrincipal();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
        }
        User user = userOpt.get();
        if (req.nickname() != null && !req.nickname().isBlank()) {
            user.setNickname(req.nickname());
        }
        if (req.gender() != null && !req.gender().isBlank()) {
            user.setGender(req.gender());
        }
        userRepository.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    // 소셜 로그인으로만 가입해 passwordHash가 없는 계정은 "현재 비밀번호"가 없으므로
    // 비밀번호 변경이 아니라 신규 설정으로 취급해 currentPassword 검증을 건너뛴다.
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(Authentication authentication, @RequestBody ChangePasswordRequest req) {
        if (req.newPassword() == null || req.newPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "새 비밀번호를 입력해주세요"));
        }
        Long userId = (Long) authentication.getPrincipal();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
        }
        User user = userOpt.get();
        if (user.getPasswordHash() != null) {
            if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
                return ResponseEntity.status(401).body(Map.of("error", "현재 비밀번호가 일치하지 않습니다"));
            }
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // 회원가입 때는 선택 업로드, 마이페이지에서는 언제든 재업로드 가능 - 둘 다 이 엔드포인트 하나로 처리한다
    // (재업로드하면 기존 S3 객체는 그대로 두고 새 객체만 추가 - 정리는 지금 범위 밖).
    @PostMapping("/photo")
    public ResponseEntity<?> uploadPhoto(Authentication authentication, @RequestParam("file") MultipartFile file) {
        ProfileImageService.ValidationError validationError = profileImageService.validate(file);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError.message()));
        }
        Long userId = (Long) authentication.getPrincipal();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
        }
        User user = userOpt.get();
        String url = profileImageService.upload(userId, file);
        user.setProfileImageUrl(url);
        userRepository.save(user);
        return ResponseEntity.ok(toResponse(user));
    }

    // 회원 자진 탈퇴. 관리자의 강제 탈퇴(AdminController.deleteUser)와 동일하게 실제 row는 지우지 않고
    // withdrawnAt만 채우는 soft-delete로 처리한다 - 탈퇴 후 1년 지나면 WithdrawnUserCleanupService가
    // 배치로 실제 삭제한다. 토큰 자체를 서버에서 무효화하는 방식은 없으므로(스테이트리스 JWT),
    // 탈퇴 처리 후 프론트에서 로컬 토큰을 지우는 것으로 즉시 로그아웃 효과를 낸다.
    @DeleteMapping
    public ResponseEntity<?> withdraw(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다"));
        }
        User user = userOpt.get();
        if (user.getWithdrawnAt() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 탈퇴 처리된 계정입니다"));
        }
        user.setWithdrawnAt(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Map<String, Object> toResponse(User user) {
        Map<String, Object> res = new HashMap<>();
        res.put("id", user.getId());
        res.put("email", user.getEmail());
        res.put("nickname", user.getNickname());
        res.put("gender", user.getGender());
        res.put("profileImageUrl", user.getProfileImageUrl());
        return res;
    }
}

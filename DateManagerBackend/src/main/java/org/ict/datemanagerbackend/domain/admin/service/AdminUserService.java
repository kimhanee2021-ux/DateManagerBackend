package org.ict.datemanagerbackend.domain.admin.service;

import org.ict.datemanagerbackend.domain.admin.dto.Request.AdminUpdateUserRequest;
import org.ict.datemanagerbackend.domain.admin.dto.Response.AdminUserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
  // <<가입일 내림차순 페이지네이션 + 이메일/닉네임 검색 + 일반/구독회원 필터>>
  Page<AdminUserDto> listUsers(String search, String field, String filter, Pageable pageable);
  // <<닉네임/성별 수정>>
  AdminUserDto updateUser(Long id, AdminUpdateUserRequest request);
  // <<탈퇴 처리(soft-delete) - 관리자 본인 계정은 대상에서 제외>>
  void withdrawUser(Long adminUserId, Long targetId);
}

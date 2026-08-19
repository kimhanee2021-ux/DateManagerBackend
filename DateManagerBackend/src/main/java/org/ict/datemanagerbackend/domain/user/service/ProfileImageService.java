package org.ict.datemanagerbackend.domain.user.service;

import org.springframework.web.multipart.MultipartFile;

// 프로필 사진 파일을 S3에 올리고, DB에 저장할 공개 URL 문자열만 돌려준다.
// 버킷 자체가 퍼블릭 읽기 정책(버킷 정책)으로 열려있다고 가정하므로 객체별 ACL은 따로 안 건다
// (버킷 만들 때 "ACL 비활성화" 권장 설정을 그대로 썼기 때문에 객체 ACL 자체가 거부됨).
public interface ProfileImageService {

    record ValidationError(String message) {
    }

    ValidationError validate(MultipartFile file);

    String upload(Long userId, MultipartFile file);
}

package org.ict.datemanagerbackend.domain.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileImageServiceImpl implements ProfileImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${spring.cloud.aws.region}")
    private String region;

    public ProfileImageServiceImpl(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public ValidationError validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ValidationError("업로드할 파일이 없습니다");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            return new ValidationError("파일 크기는 5MB 이하여야 합니다");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            return new ValidationError("jpg/png/webp/gif 이미지만 업로드할 수 있습니다");
        }
        return null;
    }

    @Override
    public String upload(Long userId, MultipartFile file) {
        String extension = extractExtension(file.getOriginalFilename());
        // 사람이 예측 못 하는 랜덤 파일명을 써서, 남의 프로필 사진 URL을 추측해서 접근/덮어쓰기하는 걸 막는다.
        String key = "profile-images/%d-%s%s".formatted(userId, UUID.randomUUID(), extension);

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("S3 업로드 중 파일을 읽지 못했습니다", e);
        }

        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : "";
    }
}

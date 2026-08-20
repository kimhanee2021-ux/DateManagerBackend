package org.ict.datemanagerbackend.domain.couple.repository;

import org.ict.datemanagerbackend.domain.couple.entity.CoupleNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoupleNotificationRepository extends JpaRepository<CoupleNotification, Long> {

    // "recipient_id = ? AND is_read = false" 인 알림만, 최신순으로.
    // 다음 로그인 시 이 메서드 결과를 그대로 화면에 몰아서 보여준다.
    List<CoupleNotification> findByRecipient_IdAndReadFalseOrderByCreatedAtDesc(Long recipientId);
}

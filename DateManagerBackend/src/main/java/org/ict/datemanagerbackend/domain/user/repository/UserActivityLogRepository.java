package org.ict.datemanagerbackend.domain.user.repository;

import org.ict.datemanagerbackend.domain.user.entity.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {
}

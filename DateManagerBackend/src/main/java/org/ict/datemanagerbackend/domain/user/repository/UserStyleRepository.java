package org.ict.datemanagerbackend.domain.user.repository;

import org.ict.datemanagerbackend.domain.user.entity.UserStyle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStyleRepository extends JpaRepository<UserStyle, Long> {
}

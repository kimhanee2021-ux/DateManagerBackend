package org.ict.datemanagerbackend.domain.user.service;

import org.ict.datemanagerbackend.domain.user.entity.User;
import org.ict.datemanagerbackend.domain.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WithdrawnUserCleanupServiceImpl implements WithdrawnUserCleanupService {

    private final UserRepository userRepository;

    public WithdrawnUserCleanupServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Scheduled(cron = "0 0 4 * * *")
    public void deleteLongWithdrawnUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(1);
        List<User> targets = userRepository.findByWithdrawnAtBefore(cutoff);
        for (User user : targets) {
            try {
                userRepository.delete(user);
            } catch (DataIntegrityViolationException e) {
                // 연결된 데이터가 남아있어 삭제 불가 - 다음 배치 때 다시 시도
            }
        }
    }
}

package org.ict.datemanagerbackend.domain.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    public MailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[Date Manager] 비밀번호 재설정 안내");
        message.setText(
                "안녕하세요, Date Manager입니다.\n\n"
                        + "아래 링크를 눌러 비밀번호를 재설정해주세요. 이 링크는 30분 동안만 유효합니다.\n\n"
                        + resetLink + "\n\n"
                        + "본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다."
        );
        try {
            mailSender.send(message);
            log.info("비밀번호 재설정 메일 발송 완료: {}", to);
        } catch (Exception e) {
            // 메일 발송 실패는 사용자에게 원인을 그대로 노출할 필요가 없다(SMTP 설정 문제 등 내부 사정) -
            // 로그만 남기고 find-password 응답 자체는 항상 "발송됐다"로 통일해서 계정 존재 여부를 숨긴다.
            log.error("비밀번호 재설정 메일 발송 실패: {}", to, e);
        }
    }
}

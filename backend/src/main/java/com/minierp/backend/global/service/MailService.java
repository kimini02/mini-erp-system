package com.minierp.backend.global.service;

import com.minierp.backend.global.exception.BusinessException;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;
    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;
    @Value("${app.mail.from:}")
    private String fromAddress;
    @Value("${spring.mail.username:}")
    private String smtpUsername;
    public void sendVerificationCodeMail(String toEmail, String verificationCode) {
        if (!mailEnabled) {
            log.info("메일 발송 비활성화 상태입니다.");
            return;
        }
        String resolvedFrom = resolveFromAddress();
        if (resolvedFrom == null || resolvedFrom.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_RESET_REQUEST, "메일 발신 주소 설정이 필요합니다.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(resolvedFrom);
        message.setSubject("[WorkFlow] 비밀번호 재설정 인증번호");
        message.setText(buildVerificationCodeBody(verificationCode));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new BusinessException(ErrorCode.INVALID_RESET_REQUEST, "인증 메일 발송에 실패했습니다.");
        }
    }

    public void sendInvitationMail(String toEmail, String loginId, String link) {
        String from = resolveFromAddress();
        if (from == null || from.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메일 발신 주소 설정이 필요합니다.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(from);
        message.setSubject("[WorkFlow] 첫 비밀번호 설정");
        message.setText("로그인 아이디: " + loginId + "\n\n"
                + "아래 링크에서 첫 비밀번호를 설정해 주세요. 유효시간은 24시간입니다.\n\n" + link);
        mailSender.send(message);
    }

    public void sendFindIdMail(String toEmail, String loginId) {
        if (!mailEnabled) {
            return;
        }
        String from = resolveFromAddress();
        if (from == null || from.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메일 발신 주소 설정이 필요합니다.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(from);
        message.setSubject("[WorkFlow] 아이디 안내");
        message.setText("요청하신 WorkFlow 아이디는 " + loginId + "입니다.\n본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        mailSender.send(message);
    }

    private String resolveFromAddress() {
        if (fromAddress != null && !fromAddress.isBlank()) {
            return fromAddress;
        }
        String envFrom = System.getenv("GMAIL_FROM");
        if (envFrom != null && !envFrom.isBlank()) {
            return envFrom;
        }
        if (smtpUsername != null && !smtpUsername.isBlank()) {
            return smtpUsername;
        }
        String envUsername = System.getenv("GMAIL_USERNAME");
        if (envUsername != null && !envUsername.isBlank()) {
            return envUsername;
        }
        return null;
    }

    private String buildVerificationCodeBody(String verificationCode) {
        return "안녕하세요. WorkFlow 비밀번호 재설정 인증번호입니다.\n\n" + "인증번호: " + verificationCode + "\n" + "유효시간: 5분\n\n"
                + "본인이 요청하지 않았다면 이 메일을 무시해주세요.";
    }
}

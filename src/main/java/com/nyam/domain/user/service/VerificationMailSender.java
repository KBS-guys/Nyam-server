package com.nyam.domain.user.service;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 인증번호를 로컬 Mailpit SMTP로 동기 전달하고 실패를 공개 가능한 오류로 변환합니다.
 */
@Component
public class VerificationMailSender {

    private static final String FROM_ADDRESS = "no-reply@nyamlog.local";
    private final JavaMailSender mailSender;

    /**
     * Spring Mail 발송기를 주입받습니다.
     *
     * @param mailSender 로컬 Mailpit과 통신하는 발송기
     */
    public VerificationMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 수신 이메일에 현재 인증번호와 유효기간 안내를 전달합니다.
     *
     * @param displayEmail 공백을 제거한 실제 수신 이메일
     * @param verificationCode 메일 본문에만 포함할 현재 6자리 인증번호
     * @throws BusinessException SMTP 전달이 실패하거나 타임아웃된 경우
     */
    public void send(String displayEmail, String verificationCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM_ADDRESS);
        message.setTo(displayEmail);
        message.setSubject("Nyamlog 이메일 인증번호");
        message.setText("Nyamlog 이메일 인증번호를 확인해 주세요.\n\n인증번호: "
                + verificationCode + "\n\n이 인증번호는 5분 동안 유효합니다.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BusinessException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
        }
    }
}

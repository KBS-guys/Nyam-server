package com.nyam.domain.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 인증번호를 설정된 SMTP로 동기 전달하고 실패를 공개 가능한 오류로 변환합니다.
 */
@Component
public class VerificationMailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    /**
     * Spring Mail 발송기와 검증된 발신 주소를 주입받습니다.
     *
     * @param mailSender 실행 환경의 SMTP 발송기
     * @param fromAddress 로컬 기본 주소 또는 provider에서 검증한 발신 주소
     */
    public VerificationMailSender(JavaMailSender mailSender,
            @Value("${nyam.mail.from:no-reply@nyamlog.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
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
        message.setFrom(fromAddress);
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

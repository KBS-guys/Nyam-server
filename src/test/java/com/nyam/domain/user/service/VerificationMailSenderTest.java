package com.nyam.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/** 환경별 발신 주소와 provider 동기 실패의 비민감 공개 오류 변환을 검증합니다. */
class VerificationMailSenderTest {

    @Test
    void usesConfiguredSender() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        var sender = new VerificationMailSender(smtp, "sender@example.invalid");
        sender.send("recipient@example.invalid", "012345");
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("sender@example.invalid");
        assertThat(message.getValue().getTo()).containsExactly("recipient@example.invalid");
    }

    @Test
    void discardsSmtpAuthenticationTlsTimeoutAndRejectionDetails() {
        String sensitiveDetail = UUID.randomUUID().toString();
        for (var failure : new org.springframework.mail.MailException[] {
                new MailAuthenticationException(sensitiveDetail),
                new MailSendException(sensitiveDetail, new javax.net.ssl.SSLHandshakeException(sensitiveDetail)),
                new MailSendException(sensitiveDetail, new java.net.SocketTimeoutException(sensitiveDetail)),
                new MailSendException(sensitiveDetail) }) {
            JavaMailSender smtp = mock(JavaMailSender.class);
            doThrow(failure).when(smtp).send(any(SimpleMailMessage.class));
            var sender = new VerificationMailSender(smtp, "sender@example.invalid");
            BusinessException result = catchThrowableOfType(
                    () -> sender.send("recipient@example.invalid", "012345"), BusinessException.class);
            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
            assertThat(result.getCause()).isNull();
            assertThat(result.getMessage().contains(sensitiveDetail)).isFalse();
        }
    }
}

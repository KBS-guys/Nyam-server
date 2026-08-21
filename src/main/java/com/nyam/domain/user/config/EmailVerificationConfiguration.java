package com.nyam.domain.user.config;

import java.security.SecureRandom;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 이메일 인증번호 생성과 로컬 Mailpit 발송에 필요한 기반 객체를 구성합니다.
 */
@Configuration
public class EmailVerificationConfiguration {

    /**
     * 인증번호와 일회성 증명 생성에 사용할 암호학적 난수 생성기를 제공합니다.
     *
     * @return 운영체제 난수원을 사용하는 안전한 난수 생성기
     */
    @Bean
    SecureRandom emailVerificationSecureRandom() {
        return new SecureRandom();
    }

    /**
     * 별도 메일 발송기가 없을 때 로컬 Mailpit용 발송기를 구성합니다.
     *
     * @param host SMTP 호스트이며 기본값은 로컬 호스트
     * @param port SMTP 포트이며 기본값은 Mailpit의 1025 포트
     * @return 인증·TLS 없이 5초 제한을 적용한 메일 발송기
     */
    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    JavaMailSender emailVerificationMailSender(
            @Value("${spring.mail.host:localhost}") String host,
            @Value("${spring.mail.port:1025}") int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);

        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "false");
        properties.setProperty("mail.smtp.starttls.enable", "false");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        return sender;
    }
}

package com.nyam.domain.user.config;

import java.security.SecureRandom;
import java.util.Properties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 이메일 인증번호 생성과 환경별 SMTP 발송에 필요한 기반 객체를 구성합니다.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class EmailVerificationConfiguration {

    /**
     * 인증번호 생성에 사용할 암호학적 난수 생성기를 제공합니다.
     *
     * @return 운영체제 난수원을 사용하는 안전한 난수 생성기
     */
    @Bean
    SecureRandom emailVerificationSecureRandom() {
        return new SecureRandom();
    }

    /**
     * 로컬 Mailpit 기본값 위에 Spring Mail의 인증·TLS·timeout 설정을 적용합니다.
     *
     * @param mailProperties 실행 환경에서 바인딩된 SMTP 설정
     * @return 외부 발송 실패를 동기적으로 호출자에게 전달하는 발송기
     */
    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    JavaMailSender emailVerificationMailSender(MailProperties mailProperties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.getHost() == null ? "localhost" : mailProperties.getHost());
        sender.setPort(mailProperties.getPort() == null ? 1025 : mailProperties.getPort());
        sender.setUsername(mailProperties.getUsername());
        sender.setPassword(mailProperties.getPassword());
        sender.setProtocol(mailProperties.getProtocol());
        sender.setDefaultEncoding(mailProperties.getDefaultEncoding().name());

        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "false");
        properties.setProperty("mail.smtp.starttls.enable", "false");
        properties.setProperty("mail.smtp.starttls.required", "false");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.putAll(mailProperties.getProperties());
        return sender;
    }
}

package com.sseukbap.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 암호화 도구 (BCrypt). spring-security-crypto 모듈만 사용한다.
 * 사용: passwordEncoder.encode(원문), passwordEncoder.matches(원문, 저장된 해시)
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

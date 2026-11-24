package com.sseukbap.auth.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 개발·시연용 메일 발송기: 실제로 보내지 않고 서버 로그(IntelliJ 콘솔)에 눈에 띄게 찍는다.
 * 시연할 때는 콘솔에서 인증번호를 보고 화면에 입력한다.
 */
@Slf4j
@Component
public class LogMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("""

                ========== [슥밥 메일 (개발용, 실제 발송 안 함)] ==========
                받는 사람: {}
                제목: {}
                {}
                ==========================================================""", to, subject, body);
    }
}

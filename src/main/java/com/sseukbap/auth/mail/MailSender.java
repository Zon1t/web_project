package com.sseukbap.auth.mail;

/**
 * 메일 보내기 (2026-11-24, 비밀번호 찾기).
 *
 * 지금은 서버 로그에 내용을 찍는 LogMailSender만 있다. (실제 메일은 보내지 않음)
 * 실제로 보내려면 spring-boot-starter-mail 의존성을 추가하고 이 인터페이스를 구현한 클래스를 하나 더 만들어
 * LogMailSender 대신 쓰면 된다. (부르는 쪽 코드는 그대로)
 */
public interface MailSender {

    /**
     * @param to      받는 사람 이메일
     * @param subject 제목
     * @param body    본문 (글자)
     */
    void send(String to, String subject, String body);
}

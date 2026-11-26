package com.sseukbap.auth.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로그인 시도 제한 (C2, 2026-11-24): 아이디별로 5번 연속 실패하면 5분 동안 로그인을 막는다.
 * - DB 없이 서버 메모리에만 둔다. (서버를 다시 켜면 풀림)
 * - 없는 아이디도 똑같이 센다. (아이디가 있는지 드러내지 않도록)
 * - 성공하면 실패 횟수를 지운다.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(5);

    /** 소문자 아이디 → 실패 기록 */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 잠겨 있으면 429 TOO_MANY_LOGIN_ATTEMPTS. 잠금 시간이 지났으면 기록을 지운다 */
    public void checkNotLocked(String loginId) {
        Attempt attempt = attempts.get(loginId);
        if (attempt == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (attempt.lockedUntil > now) {
            throw new BusinessException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }
        if (attempt.lockedUntil != 0) {
            attempts.remove(loginId, attempt);   // 잠금이 끝났으면 처음부터 다시 센다
        }
    }

    /** 실패 1회 기록. 5회째면 지금부터 5분 잠근다 */
    public void recordFailure(String loginId) {
        attempts.compute(loginId, (key, old) -> {
            Attempt attempt = (old == null) ? new Attempt() : old;
            attempt.failures++;
            if (attempt.failures >= MAX_FAILURES) {
                attempt.lockedUntil = System.currentTimeMillis() + LOCK_TIME.toMillis();
            }
            return attempt;
        });
    }

    /** 로그인 성공: 기록 삭제 */
    public void reset(String loginId) {
        attempts.remove(loginId);
    }

    private static final class Attempt {
        int failures;
        long lockedUntil;   // 0이면 잠기지 않음
    }
}

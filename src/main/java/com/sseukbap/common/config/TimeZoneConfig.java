package com.sseukbap.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 서버 기본 시간대를 한국 시간으로 고정한다. (2026-11-25, T6 P2)
 *
 * 스케줄러는 cron의 zone으로 한국 시간에 돌지만, 서비스 코드의 LocalDate.now()·LocalDateTime.now()는
 * 서버 PC의 기본 시간대를 쓴다. 발표 PC나 서버 시간대가 다르면 "오늘 식사팟"·식사 시간 판단이 어긋나므로
 * 앱이 뜰 때 JVM 기본 시간대를 Asia/Seoul로 맞춘다. (테스트에서도 같은 설정이 적용된다)
 */
@Configuration
public class TimeZoneConfig {

    public static final String ZONE = "Asia/Seoul";

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }
}

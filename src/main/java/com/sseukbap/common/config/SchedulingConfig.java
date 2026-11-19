package com.sseukbap.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 작업을 켠다. (식사 완료 처리 5분마다, 대화 기록 삭제 매일 0시 — party 담당 A)
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

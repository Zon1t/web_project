package com.sseukbap.party.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 식사팟 스케줄러. 실행 시점만 정하고, 실제 처리는 PartyBatchService가 한다.
 * (@EnableScheduling은 common/config/SchedulingConfig)
 *
 * 서버 시간대와 관계없이 한국 시간 기준으로 돈다. (cron의 zone + "지금"도 한국 시간으로 계산)
 *
 * 테스트에서는 sseukbap.scheduler.enabled=false 로 꺼진다. (src/test/resources/config/application.properties)
 * 테스트 중에 스케줄러가 돌아 공용 DB의 데이터를 바꾸는 일을 막기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sseukbap.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PartyScheduler {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final PartyBatchService partyBatchService;

    /** 식사 완료 처리: 5분마다 (00, 05, 10 ... 분) */
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    public void finishParties() {
        LocalDateTime now = LocalDateTime.now(KOREA);
        List<Long> targetIds = partyBatchService.findFinishTargetIds(now);

        int finished = 0;
        for (Long partyId : targetIds) {
            try {
                if (partyBatchService.finishParty(partyId, now)) {
                    finished++;
                }
            } catch (RuntimeException e) {
                // 한 팟에서 오류가 나도 나머지 팟은 계속 처리한다. 다음 5분 뒤에 다시 시도된다.
                log.error("식사 완료 처리 실패: partyId={}", partyId, e);
            }
        }
        if (finished > 0) {
            log.info("식사 완료 처리: {}건", finished);
        }
    }

    /** 대화 기록 삭제: 매일 0시 */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void deleteOldChats() {
        int deleted = partyBatchService.deleteOldChats(LocalDate.now(KOREA));
        log.info("대화 기록 삭제: {}건", deleted);
    }
}

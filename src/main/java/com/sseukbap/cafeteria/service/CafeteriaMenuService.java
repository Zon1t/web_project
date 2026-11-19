package com.sseukbap.cafeteria.service;

import com.sseukbap.cafeteria.dto.CafeteriaStatus;
import com.sseukbap.cafeteria.dto.CafeteriaTodayResponse;
import com.sseukbap.cafeteria.entity.CafeteriaMenu;
import com.sseukbap.cafeteria.repository.CafeteriaMenuRepository;
import com.sseukbap.common.enums.MealType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * 오늘의 구내식당 메뉴 (C 담당). API_SPEC: GET /api/cafeteria/menus/today
 *
 * 1. 오늘 메뉴가 DB에 있으면 그대로 반환 (수기 입력 데이터도 여기서 쓰인다)
 * 2. 없으면 학교 사이트에서 수집해 받은 모든 날짜를 저장 → 월요일에 한 번 성공하면 그 주는 다시 수집하지 않는다
 * 3. 학교 사이트가 아직 갱신 전이거나 실패하면 10분 동안은 다시 수집하지 않는다 (사이트 부담·응답 지연 방지)
 *
 * 상태 판단
 *   - 오늘 메뉴 있음                          → OPEN
 *   - 주말 / 이번 주 메뉴는 올라왔는데 오늘만 없음 → CLOSED (미운영일)
 *   - 이번 주 메뉴가 아직 안 올라옴(월요일 갱신 전) / 수집 실패 → UNAVAILABLE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CafeteriaMenuService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);

    private final CafeteriaMenuRepository cafeteriaMenuRepository;
    private final CafeteriaCrawler cafeteriaCrawler;
    private final CafeteriaMenuWriter cafeteriaMenuWriter;

    // 마지막으로 "오늘 메뉴 없음"이 나온 날짜·결과·다음 수집 가능 시각 (서버 메모리, 재시작하면 초기화)
    private LocalDate lastMissDate;
    private CafeteriaStatus lastMissStatus;
    private Instant retryAfter = Instant.MIN;

    public CafeteriaTodayResponse getTodayMenu() {
        LocalDate today = LocalDate.now(KOREA);

        // 1. DB에 오늘 메뉴가 있으면 그대로
        List<CafeteriaMenu> menus = cafeteriaMenuRepository.findByMenuDateOrderByMenuIdAsc(today);
        if (!menus.isEmpty()) {
            return toOpenResponse(today, menus);
        }

        // 주말은 운영하지 않으므로 수집하지 않는다
        if (isWeekend(today)) {
            return CafeteriaTodayResponse.empty(today, CafeteriaStatus.CLOSED);
        }

        // 2~3. 수집. 동시에 여러 요청이 와도 이 서버에서는 한 번만 수집하도록 잠근다
        synchronized (this) {
            menus = cafeteriaMenuRepository.findByMenuDateOrderByMenuIdAsc(today);   // 기다리는 동안 저장됐을 수 있음
            if (!menus.isEmpty()) {
                return toOpenResponse(today, menus);
            }
            if (today.equals(lastMissDate) && Instant.now().isBefore(retryAfter)) {
                return CafeteriaTodayResponse.empty(today, lastMissStatus);   // 10분 안에는 다시 수집하지 않음
            }

            CafeteriaStatus status = crawlAndSave(today);
            if (status == CafeteriaStatus.OPEN) {
                lastMissDate = null;
                return toOpenResponse(today, cafeteriaMenuRepository.findByMenuDateOrderByMenuIdAsc(today));
            }

            lastMissDate = today;
            lastMissStatus = status;
            retryAfter = Instant.now().plus(RETRY_INTERVAL);
            return CafeteriaTodayResponse.empty(today, status);
        }
    }

    /** 학교 사이트에서 수집해 DB에 없는 날짜를 모두 저장하고, 오늘 상태를 판단한다 */
    private CafeteriaStatus crawlAndSave(LocalDate today) {
        Map<LocalDate, Map<MealType, List<String>>> menusByDate;
        try {
            menusByDate = cafeteriaCrawler.fetchMenus();
        } catch (Exception e) {
            log.warn("계절밥상 메뉴 수집 실패: {}", e.toString());
            return CafeteriaStatus.UNAVAILABLE;   // 저장하지 않음 → 다음 요청(10분 뒤)에 다시 시도
        }

        menusByDate.forEach((date, meals) -> {
            if (cafeteriaMenuRepository.existsByMenuDate(date)) {
                return;   // 이미 저장된 날짜(수기 입력 포함)는 건드리지 않음
            }
            try {
                cafeteriaMenuWriter.saveDay(date, meals);
            } catch (DataIntegrityViolationException e) {
                log.info("계절밥상 {} 메뉴는 다른 요청이 먼저 저장함", date);
            }
        });

        if (menusByDate.containsKey(today)) {
            return CafeteriaStatus.OPEN;
        }
        // 이번 주(월요일 이후) 메뉴가 하나라도 있으면 갱신은 된 것 → 오늘만 없으면 미운영일
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean thisWeekPublished = menusByDate.keySet().stream().anyMatch(date -> !date.isBefore(monday));
        return thisWeekPublished ? CafeteriaStatus.CLOSED : CafeteriaStatus.UNAVAILABLE;
    }

    private CafeteriaTodayResponse toOpenResponse(LocalDate today, List<CafeteriaMenu> menus) {
        List<String> lunch = menus.stream()
                .filter(menu -> menu.getMealType() == MealType.LUNCH)
                .map(CafeteriaMenu::getMenuName)
                .toList();
        List<String> dinner = menus.stream()
                .filter(menu -> menu.getMealType() == MealType.DINNER)
                .map(CafeteriaMenu::getMenuName)
                .toList();
        return CafeteriaTodayResponse.open(today, lunch, dinner);
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}

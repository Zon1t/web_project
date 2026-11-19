package com.sseukbap.cafeteria.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sseukbap.cafeteria.entity.CafeteriaMenu;
import com.sseukbap.common.enums.MealType;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 세종대 계절밥상 메뉴 수집 (Jsoup).
 *
 * 학교 식당안내 페이지(https://www.sejong.ac.kr/kor/unilife/cafeteria-info.do)의
 * '오늘의 메뉴 확인하기' 팝업은 아래 주소에서 JSON을 받아 그린다. 화면 HTML 대신 이 JSON을 바로 읽는다.
 *   - placeId=1 : 계절밥상 (학교 사이트 app.cafeteria.js의 CAFE_MAP '202' → placeIds ['1'])
 *   - 한 번에 여러 날짜(그 주 + 지난 며칠)가 온다. 매주 월요일 중 랜덤한 시각에 갱신된다.
 *
 * 응답 예: {"items":[{"menuDate":"2026-11-19","mealTypeId":1,"mealName":"중식",
 *                      "menuName":"사골배추국 · 한식잡채 · 배추김치", ...}, ...]}
 */
@Component
public class CafeteriaCrawler {

    static final String MENU_URL =
            "https://www.sejong.ac.kr/kor/unilife/cafeteria-info.do?mode=getMenuList&placeId=1";

    private static final int TIMEOUT_MS = 5_000;
    private static final int MEAL_TYPE_LUNCH = 1;
    private static final int MEAL_TYPE_DINNER = 2;
    private static final String MENU_SEPARATOR = "·";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 학교 사이트에서 받은 날짜별·끼니별 메뉴 (날짜 오름차순)
     * 네트워크 오류, 응답 형식 변경 등은 예외로 던진다. → 호출하는 쪽에서 UNAVAILABLE 처리
     */
    public Map<LocalDate, Map<MealType, List<String>>> fetchMenus() throws IOException {
        String body = Jsoup.connect(MENU_URL)
                .ignoreContentType(true)          // HTML이 아니라 JSON 응답
                .userAgent("Mozilla/5.0 (sseukbap cafeteria menu)")
                .header("Accept", "application/json")
                .timeout(TIMEOUT_MS)
                .execute()
                .body();

        MenuListResponse response = jsonMapper.readValue(body, MenuListResponse.class);
        if (response == null || response.items() == null) {
            throw new IOException("계절밥상 메뉴 응답 형식이 예상과 다릅니다.");
        }
        return groupByDate(response.items());
    }

    private Map<LocalDate, Map<MealType, List<String>>> groupByDate(List<MenuItem> items) {
        Map<LocalDate, Map<MealType, Set<String>>> grouped = new TreeMap<>();

        for (MenuItem item : items) {
            MealType mealType = toMealType(item.mealTypeId());
            if (mealType == null || item.menuDate() == null || item.menuName() == null) {
                continue;   // 모르는 끼니·빈 값은 건너뜀
            }
            LocalDate date = LocalDate.parse(item.menuDate());   // "YYYY-MM-DD"
            Set<String> names = grouped
                    .computeIfAbsent(date, d -> new EnumMap<>(MealType.class))
                    .computeIfAbsent(mealType, m -> new LinkedHashSet<>());   // 순서 유지 + 중복 제거(UK)

            for (String name : item.menuName().split(MENU_SEPARATOR)) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed.length() > CafeteriaMenu.MENU_NAME_MAX
                            ? trimmed.substring(0, CafeteriaMenu.MENU_NAME_MAX)
                            : trimmed);
                }
            }
        }

        Map<LocalDate, Map<MealType, List<String>>> result = new TreeMap<>();
        grouped.forEach((date, meals) -> {
            Map<MealType, List<String>> mealLists = new EnumMap<>(MealType.class);
            meals.forEach((mealType, names) -> {
                if (!names.isEmpty()) {
                    mealLists.put(mealType, new ArrayList<>(names));
                }
            });
            if (!mealLists.isEmpty()) {
                result.put(date, mealLists);
            }
        });
        return result;
    }

    private MealType toMealType(Integer mealTypeId) {
        if (mealTypeId == null) {
            return null;
        }
        return switch (mealTypeId) {
            case MEAL_TYPE_LUNCH -> MealType.LUNCH;
            case MEAL_TYPE_DINNER -> MealType.DINNER;
            default -> null;
        };
    }

    // ===== 학교 사이트 JSON 구조 (필요한 필드만) =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MenuListResponse(List<MenuItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MenuItem(String menuDate, Integer mealTypeId, String menuName) {
    }
}

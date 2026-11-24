package com.sseukbap.restaurant.service;

import com.sseukbap.restaurant.entity.RestaurantCategory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 네이버 지역 검색의 분류(category)로 판단하는 규칙. (식당 등록, 2026-11-24)
 *
 * 네이버 분류는 "큰 분류>작은 분류" 모양이다. 2026-11-24 실제 호출로 확인한 예:
 *   음식점>한식, 한식>족발,보쌈, 중식>마라탕, 음식점>일식>일본식라면, 음식점>패밀리레스토랑,
 *   카페,디저트>베이커리, 음식점>카페,디저트  /  교육,학문>대학교, 대학교>부속건물, 생활,편의>편의점
 *
 * 1. 음식점인지: 첫 단계가 FOOD_ROOTS에 있으면 음식점. 분류가 비어 있으면 판단할 수 없어 통과시킨다.
 *    ('요리학원', '목욕탕'처럼 글자만 겹치는 곳을 막으려고 "포함"이 아니라 첫 단계 "일치"로 본다)
 * 2. 추천 카테고리: 분류 전체에 아래 단어가 들어 있으면 그 카테고리 (위에서부터 먼저 맞는 것). 화면에서 미리 골라 두는 용도
 */
public final class PlaceCategoryPolicy {

    /** 음식점으로 보는 네이버 분류의 첫 단계 */
    static final Set<String> FOOD_ROOTS = Set.of(
            "음식점", "한식", "중식", "일식", "양식", "분식",
            "카페,디저트", "카페", "디저트", "베이커리", "술집",
            "아시아음식", "패스트푸드", "뷔페", "치킨,닭강정", "피자", "햄버거", "퓨전음식",
            "패밀리레스토랑", "도시락,컵밥", "샐러드", "브런치",
            "육류,고기요리", "해물,생선요리", "찌개,전골", "국수"
    );

    /** 추천 카테고리 단어 (순서 중요: '음식점>카페,디저트'는 카페, '음식점>일식>일본식라면'은 일식) */
    private static final Map<RestaurantCategory, List<String>> SUGGEST_WORDS = new LinkedHashMap<>();

    static {
        SUGGEST_WORDS.put(RestaurantCategory.CAFE, List.of("카페", "디저트", "베이커리", "제과"));
        SUGGEST_WORDS.put(RestaurantCategory.SNACK, List.of("분식", "김밥", "떡볶이"));
        SUGGEST_WORDS.put(RestaurantCategory.JAPANESE, List.of("일식", "초밥", "일본식", "돈가스", "우동", "이자카야"));
        SUGGEST_WORDS.put(RestaurantCategory.CHINESE, List.of("중식", "중국", "마라탕", "양꼬치", "훠궈"));
        SUGGEST_WORDS.put(RestaurantCategory.WESTERN, List.of("양식", "이탈리아", "파스타", "피자", "햄버거", "스테이크", "패밀리레스토랑", "브런치"));
        SUGGEST_WORDS.put(RestaurantCategory.KOREAN, List.of("한식", "국밥", "감자탕", "족발", "보쌈", "백반", "칼국수", "냉면", "해장국", "곰탕", "육류,고기요리"));
    }

    private PlaceCategoryPolicy() {
    }

    /** 음식점·카페로 볼 수 있으면 true (분류가 비어 있으면 판단할 수 없어 true) */
    public static boolean isFoodPlace(String naverCategory) {
        if (naverCategory == null || naverCategory.isBlank()) {
            return true;
        }
        String root = naverCategory.split(">")[0].trim();
        return FOOD_ROOTS.contains(root);
    }

    /** 네이버 분류로 추천하는 카테고리. 음식점이 아니거나 판단할 수 없으면 null */
    public static RestaurantCategory suggest(String naverCategory) {
        if (naverCategory == null || naverCategory.isBlank() || !isFoodPlace(naverCategory)) {
            return null;
        }
        for (Map.Entry<RestaurantCategory, List<String>> entry : SUGGEST_WORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(naverCategory::contains)) {
                return entry.getKey();
            }
        }
        return null;
    }
}

package com.sseukbap.restaurant.service;

import com.sseukbap.restaurant.entity.RestaurantCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 네이버 분류 → 음식점 여부·추천 카테고리. 예시는 2026-11-24 실제 호출에서 받은 값 */
class PlaceCategoryPolicyTest {

    @Test
    void 음식점_계열_분류는_통과() {
        assertThat(PlaceCategoryPolicy.isFoodPlace("음식점>한식")).isTrue();
        assertThat(PlaceCategoryPolicy.isFoodPlace("한식>족발,보쌈")).isTrue();
        assertThat(PlaceCategoryPolicy.isFoodPlace("중식>마라탕")).isTrue();
        assertThat(PlaceCategoryPolicy.isFoodPlace("음식점>일식>일본식라면")).isTrue();
        assertThat(PlaceCategoryPolicy.isFoodPlace("카페,디저트>베이커리")).isTrue();
        assertThat(PlaceCategoryPolicy.isFoodPlace("음식점>패밀리레스토랑")).isTrue();
    }

    @Test
    void 음식점이_아닌_분류는_막는다() {
        assertThat(PlaceCategoryPolicy.isFoodPlace("교육,학문>대학교")).isFalse();
        assertThat(PlaceCategoryPolicy.isFoodPlace("대학교>부속건물")).isFalse();
        assertThat(PlaceCategoryPolicy.isFoodPlace("생활,편의>편의점")).isFalse();
        // 글자만 겹치는 곳 (첫 단계 일치로 보기 때문에 막힌다)
        assertThat(PlaceCategoryPolicy.isFoodPlace("교육,학문>요리학원")).isFalse();
        assertThat(PlaceCategoryPolicy.isFoodPlace("생활,편의>목욕탕")).isFalse();
    }

    @Test
    void 분류가_비어_있으면_판단할_수_없어_통과() {
        assertThat(PlaceCategoryPolicy.isFoodPlace("")).isTrue();
        assertThat(PlaceCategoryPolicy.isFoodPlace(null)).isTrue();
        assertThat(PlaceCategoryPolicy.suggest("")).isNull();
    }

    @Test
    void 추천_카테고리() {
        assertThat(PlaceCategoryPolicy.suggest("음식점>한식")).isEqualTo(RestaurantCategory.KOREAN);
        assertThat(PlaceCategoryPolicy.suggest("한식>족발,보쌈")).isEqualTo(RestaurantCategory.KOREAN);
        assertThat(PlaceCategoryPolicy.suggest("중식>마라탕")).isEqualTo(RestaurantCategory.CHINESE);
        assertThat(PlaceCategoryPolicy.suggest("음식점>마라탕")).isEqualTo(RestaurantCategory.CHINESE);
        assertThat(PlaceCategoryPolicy.suggest("음식점>일식>돈가스")).isEqualTo(RestaurantCategory.JAPANESE);
        assertThat(PlaceCategoryPolicy.suggest("음식점>패밀리레스토랑")).isEqualTo(RestaurantCategory.WESTERN);
        assertThat(PlaceCategoryPolicy.suggest("카페,디저트>베이커리")).isEqualTo(RestaurantCategory.CAFE);
        assertThat(PlaceCategoryPolicy.suggest("음식점>카페,디저트")).isEqualTo(RestaurantCategory.CAFE);
        assertThat(PlaceCategoryPolicy.suggest("생활,편의>편의점")).isNull();   // 음식점이 아니면 추천 없음
        assertThat(PlaceCategoryPolicy.suggest("음식점")).isNull();             // 세부 분류가 없으면 추천 없음
    }
}

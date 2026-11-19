package com.sseukbap.restaurant;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.Suggestion;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.repository.SuggestionRepository;
import com.sseukbap.restaurant.service.SuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 식당 정보 수정 제안 저장 확인. @Transactional이라 저장한 제안은 DB에 남지 않는다. */
@SpringBootTest
@Transactional
class SuggestionServiceTest {

    @Autowired SuggestionService suggestionService;
    @Autowired SuggestionRepository suggestionRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;

    Long hongId;
    Long user01;

    @BeforeEach
    void setUp() {
        hongId = restaurantRepository.findAll().stream()
                .filter(r -> r.getName().equals("홍반점"))
                .findFirst()
                .map(Restaurant::getRestaurantId)
                .orElseThrow();
        user01 = memberRepository.findByLoginId("user01").map(Member::getMemberId).orElseThrow();
    }

    @Test
    void 제안을_공백을_지우고_저장한다() {
        Long suggestionId = suggestionService
                .createSuggestion(hongId, user01, "  카테고리가 중식이 아니라 한식이에요  ")
                .suggestionId();

        Suggestion saved = suggestionRepository.findById(suggestionId).orElseThrow();
        assertThat(saved.getContent()).isEqualTo("카테고리가 중식이 아니라 한식이에요");
        assertThat(saved.getRestaurant().getRestaurantId()).isEqualTo(hongId);
        assertThat(saved.getMember().getMemberId()).isEqualTo(user01);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 내용이_500자면_저장된다() {
        assertThat(suggestionService.createSuggestion(hongId, user01, "가".repeat(500)).suggestionId()).isNotNull();
    }

    @Test
    void 내용이_없거나_500자_초과면_400() {
        String message = "제안 내용을 입력해 주세요.";
        assertError(() -> suggestionService.createSuggestion(hongId, user01, null), ErrorCode.INVALID_REQUEST, message);
        assertError(() -> suggestionService.createSuggestion(hongId, user01, "   "), ErrorCode.INVALID_REQUEST, message);
        assertError(() -> suggestionService.createSuggestion(hongId, user01, "가".repeat(501)), ErrorCode.INVALID_REQUEST, message);
    }

    @Test
    void 없는_식당이면_404() {
        assertError(() -> suggestionService.createSuggestion(-1L, user01, "주소가 바뀌었어요"),
                ErrorCode.RESTAURANT_NOT_FOUND, null);
    }

    /** message가 null이면 문구는 확인하지 않는다 */
    private void assertError(Runnable action, ErrorCode errorCode, String message) {
        var assertion = assertThatThrownBy(action::run).isInstanceOf(BusinessException.class);
        if (message != null) {
            assertion.hasMessage(message);
        }
        assertion.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(errorCode);
    }
}

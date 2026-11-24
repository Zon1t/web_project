package com.sseukbap.restaurant;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.restaurant.dto.InterestStatusResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.InterestRepository;
import com.sseukbap.restaurant.service.InterestService;
import com.sseukbap.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 관심 등록·해제 확인. 테스트가 끝나면 @Transactional로 되돌려서 DB에 남지 않는다.
 * 2026-11-24 (C4): 더미데이터(홍반점, user01·user03) 대신 테스트 안에서 만든 식당·회원을 쓴다.
 */
@SpringBootTest
@Transactional
class InterestServiceTest {

    @Autowired InterestService interestService;
    @Autowired InterestRepository interestRepository;
    @Autowired TestData data;

    Long restaurantId;
    Long me;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = data.restaurant("관심테스트" + TestData.uniqueNumber(), RestaurantCategory.KOREAN);
        Member member = data.member();
        restaurantId = restaurant.getRestaurantId();
        me = member.getMemberId();
    }

    @Test
    void 등록하면_관심수가_1_늘고_해제하면_원래대로() {
        InterestStatusResponse created = interestService.createInterest(restaurantId, me);
        assertThat(created.interested()).isTrue();
        assertThat(created.interestCount()).isEqualTo(1);
        assertThat(interestRepository.existsByRestaurant_RestaurantIdAndMember_MemberId(restaurantId, me)).isTrue();

        InterestStatusResponse deleted = interestService.deleteInterest(restaurantId, me);
        assertThat(deleted.interested()).isFalse();
        assertThat(deleted.interestCount()).isZero();
        assertThat(interestRepository.existsByRestaurant_RestaurantIdAndMember_MemberId(restaurantId, me)).isFalse();
    }

    @Test
    void 이미_등록했으면_409() {
        interestService.createInterest(restaurantId, me);
        assertError(() -> interestService.createInterest(restaurantId, me), ErrorCode.ALREADY_INTERESTED);
    }

    @Test
    void 등록_안_했는데_해제하면_404() {
        assertError(() -> interestService.deleteInterest(restaurantId, me), ErrorCode.INTEREST_NOT_FOUND);
    }

    @Test
    void 없는_식당이면_등록은_RESTAURANT_NOT_FOUND_해제는_INTEREST_NOT_FOUND() {
        assertError(() -> interestService.createInterest(-1L, me), ErrorCode.RESTAURANT_NOT_FOUND);
        assertError(() -> interestService.deleteInterest(-1L, me), ErrorCode.INTEREST_NOT_FOUND);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}

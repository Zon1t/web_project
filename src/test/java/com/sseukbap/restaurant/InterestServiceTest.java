package com.sseukbap.restaurant;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.dto.InterestStatusResponse;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.repository.InterestRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.service.InterestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 관심 등록·해제 확인. 테스트가 끝나면 @Transactional로 되돌려서 DB에 남지 않는다.
 * 더미데이터 기준: user01은 홍반점 관심 등록됨, user03은 안 됨.
 */
@SpringBootTest
@Transactional
class InterestServiceTest {

    @Autowired InterestService interestService;
    @Autowired InterestRepository interestRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired MemberRepository memberRepository;

    Long hongId;
    Long user01;
    Long user03;

    @BeforeEach
    void setUp() {
        hongId = restaurantRepository.findAll().stream()
                .filter(r -> r.getName().equals("홍반점"))
                .findFirst()
                .map(Restaurant::getRestaurantId)
                .orElseThrow();
        user01 = memberRepository.findByLoginId("user01").map(Member::getMemberId).orElseThrow();
        user03 = memberRepository.findByLoginId("user03").map(Member::getMemberId).orElseThrow();
    }

    @Test
    void 등록하면_관심수가_1_늘고_해제하면_원래대로() {
        long before = interestRepository.countByRestaurant_RestaurantId(hongId);

        InterestStatusResponse created = interestService.createInterest(hongId, user03);
        assertThat(created.interested()).isTrue();
        assertThat(created.interestCount()).isEqualTo(before + 1);
        assertThat(interestRepository.existsByRestaurant_RestaurantIdAndMember_MemberId(hongId, user03)).isTrue();

        InterestStatusResponse deleted = interestService.deleteInterest(hongId, user03);
        assertThat(deleted.interested()).isFalse();
        assertThat(deleted.interestCount()).isEqualTo(before);
        assertThat(interestRepository.existsByRestaurant_RestaurantIdAndMember_MemberId(hongId, user03)).isFalse();
    }

    @Test
    void 이미_등록했으면_409() {
        // user01은 더미데이터에서 이미 홍반점 관심 등록
        assertError(() -> interestService.createInterest(hongId, user01), ErrorCode.ALREADY_INTERESTED);
    }

    @Test
    void 등록_안_했는데_해제하면_404() {
        assertError(() -> interestService.deleteInterest(hongId, user03), ErrorCode.INTEREST_NOT_FOUND);
    }

    @Test
    void 없는_식당이면_등록은_RESTAURANT_NOT_FOUND_해제는_INTEREST_NOT_FOUND() {
        assertError(() -> interestService.createInterest(-1L, user03), ErrorCode.RESTAURANT_NOT_FOUND);
        assertError(() -> interestService.deleteInterest(-1L, user03), ErrorCode.INTEREST_NOT_FOUND);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}

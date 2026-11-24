package com.sseukbap.support;

import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.review.entity.Review;
import com.sseukbap.review.entity.ReviewTag;
import com.sseukbap.review.repository.ReviewRepository;
import com.sseukbap.review.repository.ReviewTagRepository;
import com.sseukbap.review.repository.TagRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 테스트가 쓸 데이터를 테스트 안에서 만드는 도우미 (C4, 2026-11-24).
 *
 * DB에 어떤 더미·시연 데이터(04, 05)가 들어 있든 테스트가 통과하도록, 필요한 회원·식당·리뷰는 여기서 새로 만든다.
 * 이름·아이디·지도 URL에 무작위 번호를 붙여 기존 데이터와 겹치지 않게 한다.
 * 테스트 클래스에 @Transactional을 붙이면 끝날 때 모두 되돌려져 DB에 남지 않는다.
 *
 *   @Autowired TestData data;
 *   Member me = data.member();
 *   Restaurant r = data.restaurant("국밥", RestaurantCategory.KOREAN);
 *   data.review(r, me, 1L, 6L);   // 태그 1, 6을 단 리뷰
 */
@Component
public class TestData {

    public static final String PASSWORD = "abcd1234";

    private final MemberRepository memberRepository;
    private final RestaurantRepository restaurantRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewTagRepository reviewTagRepository;
    private final TagRepository tagRepository;
    private final PasswordEncoder passwordEncoder;

    public TestData(MemberRepository memberRepository, RestaurantRepository restaurantRepository,
                    ReviewRepository reviewRepository, ReviewTagRepository reviewTagRepository,
                    TagRepository tagRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.restaurantRepository = restaurantRepository;
        this.reviewRepository = reviewRepository;
        this.reviewTagRepository = reviewTagRepository;
        this.tagRepository = tagRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 겹치지 않는 8자리 번호 */
    public static String uniqueNumber() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }

    /** 새 회원 (아이디 "td" + 번호, 닉네임 "테" + 번호, 비밀번호 PASSWORD) */
    public Member member() {
        String n = uniqueNumber();
        return memberRepository.saveAndFlush(Member.create("td" + n, passwordEncoder.encode(PASSWORD), "테" + n));
    }

    /** 새 식당. 이름은 그대로 쓰고, 지도 URL만 무작위 (회사 근처 좌표, 등록자는 새 회원) */
    public Restaurant restaurant(String name, RestaurantCategory category) {
        return restaurantRepository.saveAndFlush(Restaurant.create(
                name, category,
                "https://map.naver.com/p/entry/place/77" + uniqueNumber(),
                "서울특별시 광진구 테스트로 1", 37.5500, 127.0730, member()));
    }

    /** 새 리뷰 + 태그 */
    public Review review(Restaurant restaurant, Member member, Long... tagIds) {
        Review review = reviewRepository.saveAndFlush(Review.create(restaurant, member, "테스트 리뷰", null));
        for (Long tagId : tagIds) {
            reviewTagRepository.saveAndFlush(ReviewTag.create(review, tagRepository.findById(tagId).orElseThrow()));
        }
        return review;
    }
}

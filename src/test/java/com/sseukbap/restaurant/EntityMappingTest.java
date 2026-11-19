package com.sseukbap.restaurant;

import com.sseukbap.member.entity.Member;
import com.sseukbap.member.repository.MemberRepository;
import com.sseukbap.restaurant.entity.Interest;
import com.sseukbap.restaurant.entity.Restaurant;
import com.sseukbap.restaurant.entity.RestaurantCategory;
import com.sseukbap.restaurant.entity.Suggestion;
import com.sseukbap.restaurant.repository.InterestRepository;
import com.sseukbap.restaurant.repository.RestaurantRepository;
import com.sseukbap.restaurant.repository.SuggestionRepository;
import com.sseukbap.review.entity.Review;
import com.sseukbap.review.entity.ReviewTag;
import com.sseukbap.review.entity.Tag;
import com.sseukbap.review.entity.TagCategory;
import com.sseukbap.review.repository.ReviewRepository;
import com.sseukbap.review.repository.ReviewTagRepository;
import com.sseukbap.review.repository.TagRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B 엔티티가 DB 테이블(docs/sql/01_ddl.sql)과 맞게 연결됐는지 확인한다.
 *
 * 실행 전 준비: 로컬 DB에 docs/sql 01 ~ 04 스크립트 실행, application-local.yml 작성
 * 실행: IntelliJ에서 클래스 옆 ▶ 버튼, 또는 gradlew.bat test --tests "*EntityMappingTest"
 *
 * @Transactional: 테스트가 끝나면 저장한 데이터를 모두 되돌린다(rollback). DB 데이터는 바뀌지 않는다.
 */
@SpringBootTest
@Transactional
class EntityMappingTest {

    @Autowired EntityManager em;
    @Autowired MemberRepository memberRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired InterestRepository interestRepository;
    @Autowired SuggestionRepository suggestionRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired TagRepository tagRepository;
    @Autowired ReviewTagRepository reviewTagRepository;

    @Test
    void 기존_데이터를_읽을_수_있다() {
        // 태그는 1~13 고정값
        List<Tag> tags = tagRepository.findAll();
        assertThat(tags).hasSize(13);
        Tag tag1 = tagRepository.findById(1L).orElseThrow();
        assertThat(tag1.getTagName()).isEqualTo("점심추천");
        assertThat(tag1.getTagCategory()).isEqualTo(TagCategory.MEAL_TYPE);

        // 구내식당은 초기 데이터, 등록자 없음
        List<Restaurant> restaurants = restaurantRepository.findAll();
        Restaurant cafeteria = restaurants.stream()
                .filter(Restaurant::isCafeteria)
                .findFirst()
                .orElseThrow();
        assertThat(cafeteria.getMember()).isNull();

        // 더미 식당 10곳 + 구내식당 1곳 이상
        assertThat(restaurants).hasSizeGreaterThanOrEqualTo(11);
        restaurants.forEach(r -> {
            assertThat(r.getName()).isNotBlank();
            assertThat(r.getMapUrl()).isNotBlank();
            assertThat(r.getCreatedAt()).isNotNull();
        });

        // 리뷰 → 식당·작성자 연결 (LAZY 로딩)
        List<Review> reviews = reviewRepository.findAll();
        assertThat(reviews).isNotEmpty();
        reviews.forEach(review -> {
            assertThat(review.getRestaurant().getName()).isNotBlank();
            assertThat(review.getMember().getNickname()).isNotBlank();
        });

        // 리뷰태그 → 리뷰·태그 연결
        reviewTagRepository.findAll().forEach(rt -> {
            assertThat(rt.getReview().getContent()).isNotBlank();
            assertThat(rt.getTag().getTagName()).isNotBlank();
        });

        // 관심·수정 제안은 더미데이터에 없을 수도 있으니 읽기만 확인
        interestRepository.findAll().forEach(i -> assertThat(i.getRestaurant().getName()).isNotBlank());
        suggestionRepository.findAll().forEach(s -> assertThat(s.getContent()).isNotBlank());
    }

    @Test
    void 새_데이터를_저장할_수_있다() {
        Member member = memberRepository.findByLoginId("user01").orElseThrow();
        Tag tag = tagRepository.findById(5L).orElseThrow();

        Restaurant restaurant = restaurantRepository.save(Restaurant.create(
                "테스트식당", RestaurantCategory.KOREAN,
                "https://map.naver.com/p/entry/place/9999999999",
                "서울 광진구 테스트로 1", 37.5502, 127.0731, member));
        Interest interest = interestRepository.save(Interest.create(restaurant, member));
        Suggestion suggestion = suggestionRepository.save(Suggestion.create(restaurant, member, "카테고리가 달라요"));
        Review review = reviewRepository.save(Review.create(restaurant, member, "맛있어요", null));
        ReviewTag reviewTag = reviewTagRepository.save(ReviewTag.create(review, tag));

        em.flush();   // INSERT SQL을 지금 DB로 보낸다 (컬럼·시퀀스·제약조건 오류가 여기서 드러남)
        em.clear();   // 1차 캐시를 비워서 아래 조회가 실제 DB에서 읽게 한다

        Restaurant found = restaurantRepository.findById(restaurant.getRestaurantId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("테스트식당");
        assertThat(found.getCategory()).isEqualTo(RestaurantCategory.KOREAN);
        assertThat(found.isCafeteria()).isFalse();
        assertThat(found.getLat()).isEqualTo(37.5502);
        assertThat(found.getMember().getLoginId()).isEqualTo("user01");

        assertThat(interestRepository.findById(interest.getInterestId())).isPresent();
        assertThat(suggestionRepository.findById(suggestion.getSuggestionId())).isPresent();

        Review foundReview = reviewRepository.findById(review.getReviewId()).orElseThrow();
        assertThat(foundReview.getImagePath()).isNull();
        assertThat(foundReview.isWrittenBy(member.getMemberId())).isTrue();

        ReviewTag foundReviewTag = reviewTagRepository.findById(reviewTag.getReviewTagId()).orElseThrow();
        assertThat(foundReviewTag.getTag().getTagName()).isEqualTo("혼밥가능");
    }
}

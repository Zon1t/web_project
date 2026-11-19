package com.sseukbap.review.entity;

import com.sseukbap.member.entity.Member;
import com.sseukbap.restaurant.entity.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 리뷰 (REVIEW 테이블). 기준: docs/sql/01_ddl.sql
 *
 * - 같은 식당에 여러 번 작성할 수 있다.
 * - 수정 기능은 없고, 작성자만 삭제할 수 있다.
 * - 태그는 ReviewTag로 따로 저장한다. (리뷰 삭제 시 DB가 REVIEW_TAG를 CASCADE로 함께 삭제)
 */
@Entity
@Table(name = "REVIEW")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_REVIEW_GENERATOR")
    @SequenceGenerator(name = "SEQ_REVIEW_GENERATOR", sequenceName = "SEQ_REVIEW", allocationSize = 1)
    @Column(name = "REVIEW_ID")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESTAURANT_ID", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID", nullable = false)
    private Member member;            // 작성자

    @Column(name = "CONTENT", nullable = false, length = 300)
    private String content;           // 한줄평, 최대 100자

    @Column(name = "IMAGE_PATH", length = 300)
    private String imagePath;         // 사진 경로 (예: /uploads/reviews/파일명.jpg), 없으면 null

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Review(Restaurant restaurant, Member member, String content, String imagePath) {
        this.restaurant = restaurant;
        this.member = member;
        this.content = content;
        this.imagePath = imagePath;
        this.createdAt = LocalDateTime.now();
    }

    /** 리뷰 작성. 사진이 없으면 imagePath = null */
    public static Review create(Restaurant restaurant, Member member, String content, String imagePath) {
        return new Review(restaurant, member, content, imagePath);
    }

    /** 작성자 본인인지 확인 (리뷰 삭제 권한, 목록의 mine 필드) */
    public boolean isWrittenBy(Long memberId) {
        return member.getMemberId().equals(memberId);
    }
}

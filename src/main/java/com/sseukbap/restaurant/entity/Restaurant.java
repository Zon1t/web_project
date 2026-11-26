package com.sseukbap.restaurant.entity;

import com.sseukbap.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.type.YesNoConverter;

import java.time.LocalDateTime;

/**
 * 식당 (RESTAURANT 테이블). 기준: docs/sql/01_ddl.sql
 *
 * - 구내식당은 03_init_data.sql의 초기 데이터로 들어가며 category = CAFETERIA, 등록자(member) = null
 * - 등록 후 수정·삭제 기능은 없다. (수정 제안은 Suggestion)
 */
@Entity
@Table(name = "RESTAURANT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_RESTAURANT_GENERATOR")
    @SequenceGenerator(name = "SEQ_RESTAURANT_GENERATOR", sequenceName = "SEQ_RESTAURANT", allocationSize = 1)
    @Column(name = "RESTAURANT_ID")
    private Long restaurantId;

    @Column(name = "NAME", nullable = false, length = 90)
    private String name;              // 최대 30자

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 20)
    private RestaurantCategory category;

    @Column(name = "MAP_URL", nullable = false, length = 500)
    private String mapUrl;            // 네이버 지도 URL (중복 불가)

    @Column(name = "ADDRESS", length = 300)
    private String address;           // 네이버 API로 조회한 주소

    @Column(name = "LAT")
    private Double lat;               // 위도

    @Column(name = "LNG")
    private Double lng;               // 경도

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID")
    private Member member;            // 등록자. 구내식당 초기 데이터는 null

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Convert(converter = YesNoConverter.class)
    @Column(name = "HIDDEN_YN", nullable = false, length = 1)
    private boolean hidden;           // 관리자 숨김 (2026-11-24, 08_account_admin.sql). true면 목록·랭킹·룰렛 등에서 뺀다

    private Restaurant(String name, RestaurantCategory category, String mapUrl,
                       String address, Double lat, Double lng, Member member) {
        this.name = name;
        this.category = category;
        this.mapUrl = mapUrl;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.member = member;
        this.createdAt = LocalDateTime.now();
    }

    /** 식당 등록. address·lat·lng는 네이버 API 조회 결과, member는 로그인 회원 */
    public static Restaurant create(String name, RestaurantCategory category, String mapUrl,
                                    String address, Double lat, Double lng, Member member) {
        return new Restaurant(name, category, mapUrl, address, lat, lng, member);
    }

    /** 관리자 숨김·숨김 해제 (2026-11-24 A3) */
    public void changeHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /** 관리자 카테고리 변경 (2026-11-24 A3). CAFETERIA 관련 검사는 관리자 Service에서 먼저 한다 */
    public void changeCategory(RestaurantCategory category) {
        this.category = category;
    }

    /** 구내식당 여부 (응답의 cafeteria 필드). DB에 별도 컬럼은 없다. */
    public boolean isCafeteria() {
        return category == RestaurantCategory.CAFETERIA;
    }
}

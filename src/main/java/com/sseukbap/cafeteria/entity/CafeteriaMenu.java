package com.sseukbap.cafeteria.entity;

import com.sseukbap.common.enums.MealType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 구내식당 메뉴 (CAFETERIA_MENU 테이블). 기준: docs/sql/01_ddl.sql
 * 메뉴 1개 = 1행. UK_CAFETERIA_MENU (MENU_DATE, MEAL_TYPE, MENU_NAME)
 */
@Entity
@Table(name = "CAFETERIA_MENU")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CafeteriaMenu {

    /** MENU_NAME은 VARCHAR2(150 byte) → 한글 기준 50자 */
    public static final int MENU_NAME_MAX = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_CAFETERIA_MENU_GENERATOR")
    @SequenceGenerator(name = "SEQ_CAFETERIA_MENU_GENERATOR", sequenceName = "SEQ_CAFETERIA_MENU", allocationSize = 1)
    @Column(name = "MENU_ID")
    private Long menuId;

    @Column(name = "MENU_DATE", nullable = false)
    private LocalDate menuDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "MEAL_TYPE", nullable = false, length = 10)
    private MealType mealType;

    @Column(name = "MENU_NAME", nullable = false, length = 150)
    private String menuName;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;      // 수집일시

    private CafeteriaMenu(LocalDate menuDate, MealType mealType, String menuName) {
        this.menuDate = menuDate;
        this.mealType = mealType;
        this.menuName = menuName;
        this.createdAt = LocalDateTime.now();
    }

    public static CafeteriaMenu create(LocalDate menuDate, MealType mealType, String menuName) {
        return new CafeteriaMenu(menuDate, mealType, menuName);
    }
}

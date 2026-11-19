package com.sseukbap.cafeteria.repository;

import com.sseukbap.cafeteria.entity.CafeteriaMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CafeteriaMenuRepository extends JpaRepository<CafeteriaMenu, Long> {

    /** 하루치 메뉴. 저장 순서(= 학교 사이트 표시 순서)대로 */
    List<CafeteriaMenu> findByMenuDateOrderByMenuIdAsc(LocalDate menuDate);

    /** 이 날짜 메뉴가 이미 저장돼 있는지 (수집 결과 중복 저장 방지) */
    boolean existsByMenuDate(LocalDate menuDate);
}

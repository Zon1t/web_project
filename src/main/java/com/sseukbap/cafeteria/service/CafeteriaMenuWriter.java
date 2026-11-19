package com.sseukbap.cafeteria.service;

import com.sseukbap.cafeteria.entity.CafeteriaMenu;
import com.sseukbap.cafeteria.repository.CafeteriaMenuRepository;
import com.sseukbap.common.enums.MealType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 하루치 메뉴 저장. 날짜마다 트랜잭션을 따로 둔다.
 * → 다른 요청이 같은 날짜를 먼저 저장해 UK 위반이 나도 그 날짜만 롤백되고 나머지 날짜는 저장된다.
 */
@Component
@RequiredArgsConstructor
public class CafeteriaMenuWriter {

    private final CafeteriaMenuRepository cafeteriaMenuRepository;

    @Transactional
    public void saveDay(LocalDate date, Map<MealType, List<String>> meals) {
        meals.forEach((mealType, names) ->
                names.forEach(name ->
                        cafeteriaMenuRepository.save(CafeteriaMenu.create(date, mealType, name))));
        cafeteriaMenuRepository.flush();   // UK 위반을 여기서 바로 확인
    }
}

package com.sseukbap.common.interceptor;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /api/** 요청이 Controller에 도착하기 전에 로그인 여부를 확인한다.
 * 세션에 회원 ID가 없으면 401 UNAUTHORIZED (GlobalExceptionHandler가 공통 응답으로 변환).
 *
 * 확인하지 않는 주소(회원가입·로그인·중복 확인)는 WebConfig에서 지정한다.
 */
public class LoginCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);   // 세션이 없으면 새로 만들지 않는다

        if (session == null || session.getAttribute(SessionConst.LOGIN_MEMBER_ID) == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return true;
    }
}

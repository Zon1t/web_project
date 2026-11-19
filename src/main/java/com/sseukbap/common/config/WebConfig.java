package com.sseukbap.common.config;

import com.sseukbap.common.interceptor.LoginCheckInterceptor;
import com.sseukbap.common.interceptor.LoginMemberArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 설정: 로그인 체크 인터셉터와 @LoginMember 리졸버를 등록한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor())
                .addPathPatterns("/api/**")
                // API_SPEC 공통 규칙: 회원가입·아이디 중복 확인·닉네임 중복 확인·로그인만 로그인 없이 호출 가능
                .excludePathPatterns(
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/check-id",
                        "/api/auth/check-nickname"
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginMemberArgumentResolver());
    }
}

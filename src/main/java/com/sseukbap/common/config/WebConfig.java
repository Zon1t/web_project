package com.sseukbap.common.config;

import com.sseukbap.common.interceptor.LoginCheckInterceptor;
import com.sseukbap.common.interceptor.LoginMemberArgumentResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.List;

/**
 * Spring MVC 설정: 로그인 체크 인터셉터, @LoginMember 리졸버, 업로드 파일 제공.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 업로드 파일 저장 폴더 (application.yml: upload.dir). 파일 저장하는 쪽도 같은 값을 쓴다 */
    @Value("${upload.dir}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor())
                .addPathPatterns("/api/**")
                // API_SPEC 공통 규칙: 회원가입·아이디 중복 확인·닉네임 중복 확인·로그인만 로그인 없이 호출 가능
                .excludePathPatterns(
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/check-id",
                        "/api/auth/check-nickname",
                        "/api/auth/password-reset/**",  // 비밀번호 찾기 (2026-11-24): 로그인 전에 쓰는 기능
                        "/api/auth/find-id"             // 아이디 찾기 (2026-11-24)
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginMemberArgumentResolver());
    }

    /**
     * /uploads/** 주소로 업로드 폴더의 파일을 보여 준다.
     * 예: uploads/reviews/7.jpg 파일 → http://localhost:8081/uploads/reviews/7.jpg (응답의 imageUrl)
     * /api가 아니라서 로그인 체크 대상이 아니다. (<img src>로 불러오는 파일)
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";   // 폴더 주소는 반드시 '/'로 끝나야 그 안의 파일을 찾는다
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}

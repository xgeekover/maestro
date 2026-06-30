package io.maestro.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * REST CORS 허용 — 데스크탑(Electron/브라우저 개발서버)에서 다른 오리진으로 API 호출 가능하게 함.
 * 신뢰 모델(ADR-0001 D2: 신뢰된 사용자/내부 도구) 기준 허용. 외부 공개 시 오리진 제한 필요.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}

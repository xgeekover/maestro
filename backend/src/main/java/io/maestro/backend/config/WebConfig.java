package io.maestro.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * REST CORS — 데스크탑(개발 서버/Electron)에서의 호출을 허용하되, 오리진은 설정으로 제한한다(QA M-6).
 * 기본은 localhost 개발 오리진. 외부/Electron 배포 시 {@code maestro.cors.allowed-origins}로 지정.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MaestroProperties props;

    public WebConfig(MaestroProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.getCors().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}

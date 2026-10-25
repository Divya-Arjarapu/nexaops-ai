package com.nexaops.backend.admin;

import java.util.List;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminSecurityConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<AdminAuthService> authServiceProvider;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    public AdminSecurityConfiguration(
            ObjectProvider<AdminAuthService> authServiceProvider,
            ObjectMapper objectMapper,
            @Value("${nexaops.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") List<String> allowedOrigins
    ) {
        this.authServiceProvider = authServiceProvider;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor(authServiceProvider, objectMapper))
                .addPathPatterns("/api/documents", "/api/documents/**", "/api/admin/documents/**", "/api/admin/logout");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .allowCredentials(false)
                .maxAge(3600);
    }
}

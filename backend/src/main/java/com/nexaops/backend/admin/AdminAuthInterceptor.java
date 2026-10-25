package com.nexaops.backend.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

public class AdminAuthInterceptor implements HandlerInterceptor {

    private final ObjectProvider<AdminAuthService> authServiceProvider;
    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(ObjectProvider<AdminAuthService> authServiceProvider, ObjectMapper objectMapper) {
        this.authServiceProvider = authServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : null;
        if (authServiceProvider.getObject().isValid(token)) return true;

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new AdminErrorResponse(
                401, "Unauthorized", "Admin sign-in is required or has expired. Please sign in again."));
        return false;
    }
}

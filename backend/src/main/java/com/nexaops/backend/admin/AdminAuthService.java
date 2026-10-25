package com.nexaops.backend.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String configuredUsername;
    private final String configuredPassword;
    private final ConcurrentHashMap<String, Instant> sessions = new ConcurrentHashMap<>();

    public AdminAuthService(
            @Value("${nexaops.admin.username:}") String configuredUsername,
            @Value("${nexaops.admin.password:}") String configuredPassword
    ) {
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
    }

    public AdminLoginResponse login(String username, String password) {
        if (configuredUsername.isBlank() || configuredPassword.isBlank()) {
            throw new AdminAuthException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Admin sign-in is not configured. Set NEXAOPS_ADMIN_USERNAME and NEXAOPS_ADMIN_PASSWORD for the backend.");
        }
        if (!constantTimeEquals(configuredUsername, username) || !constantTimeEquals(configuredPassword, password)) {
            throw new AdminAuthException(HttpStatus.UNAUTHORIZED, "The username or password is incorrect.");
        }

        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = Instant.now().plus(TOKEN_LIFETIME);
        sessions.put(token, expiresAt);
        return new AdminLoginResponse(token, expiresAt);
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        Instant expiresAt = sessions.get(token);
        if (expiresAt == null) return false;
        if (Instant.now().isBefore(expiresAt)) return true;
        sessions.remove(token, expiresAt);
        return false;
    }

    public void logout(String token) {
        if (token != null) sessions.remove(token);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}

package com.nexaops.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAuthServiceTest {

    @Test
    void createsOpaqueExpiringSessionAndSupportsLogout() {
        AdminAuthService service = new AdminAuthService("owner", "private-secret");
        AdminLoginResponse session = service.login("owner", "private-secret");
        assertTrue(service.isValid(session.token()));
        assertTrue(session.expiresAt().isAfter(java.time.Instant.now()));
        assertNotEquals("private-secret", session.token());
        service.logout(session.token());
        assertFalse(service.isValid(session.token()));
    }

    @Test
    void rejectsInvalidCredentialsWithoutDisclosingWhichValueFailed() {
        AdminAuthService service = new AdminAuthService("owner", "private-secret");
        AdminAuthException exception = assertThrows(AdminAuthException.class,
                () -> service.login("owner", "wrong"));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        assertEquals("The username or password is incorrect.", exception.getMessage());
    }

    @Test
    void refusesLoginWhenAdminConfigurationIsMissing() {
        AdminAuthService service = new AdminAuthService("", "");
        AdminAuthException exception = assertThrows(AdminAuthException.class,
                () -> service.login("owner", "private-secret"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertTrue(exception.getMessage().contains("NEXAOPS_ADMIN_USERNAME"));
        assertFalse(service.isValid("anything"));
    }
}

package com.nexaops.backend.admin;

import java.time.Instant;

public record AdminLoginResponse(String token, Instant expiresAt) { }

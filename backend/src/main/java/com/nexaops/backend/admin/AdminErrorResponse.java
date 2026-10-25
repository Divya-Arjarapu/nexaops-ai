package com.nexaops.backend.admin;

public record AdminErrorResponse(int status, String error, String message) { }

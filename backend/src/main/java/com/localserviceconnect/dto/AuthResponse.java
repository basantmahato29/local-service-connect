package com.localserviceconnect.dto;

public record AuthResponse(
    String token,
    String role,
    String name,
    Long id
) {}

package com.tripagent.security;

public record JwtToken(String accessToken, String tokenType, long expiresIn) {
}

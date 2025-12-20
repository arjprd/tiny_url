package com.example.tinyurl.util;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class CustomAuthentication implements Authentication {
    private boolean authenticated = true;
    final private String randomToken;
    final private Long userId;

    public CustomAuthentication(String userId, String randomToken) {
        this.userId = Long.parseLong(userId);
        this.randomToken = randomToken;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    @Override
    public @Nullable String getCredentials() {
        return randomToken;
    }

    @Override
    public @Nullable Object getDetails() {
        return null;
    }

    @Override
    public @Nullable Object getPrincipal() {
        return userId;
    }

    @Override
    public boolean isAuthenticated() {
        return this.authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    public Long getUserId() {
        return userId;
    }
}

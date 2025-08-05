package org.croissantbuddies.snappfood.dto;

import org.croissantbuddies.snappfood.entity.User;

public class StatusResult {
    private int statusCode;
    private String message;
    private Long userId;
    private String token;
    private User user;

    public StatusResult(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public StatusResult(int statusCode, String message, Long userId) {
        this(statusCode, message);
        this.userId = userId;
    }

    public StatusResult(int statusCode, String message, Long userId, String token) {
        this(statusCode, message, userId);
        this.token = token;
    }

    public StatusResult(int statusCode, String message, Long userId, String token, User user) {
        this(statusCode, message, userId, token);
        this.user = user;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    public Long getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

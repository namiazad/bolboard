package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActiveSession {
    private final String userId;
    private final String sessionId;

    public ActiveSession(
            @JsonProperty("userId") final String userId,
            @JsonProperty("sessionId") final String sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
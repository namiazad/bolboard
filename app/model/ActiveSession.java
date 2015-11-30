package model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

public class ActiveSession {
    public static final String userIdDisplayName = "userId";
    public static final String sessionIdDisplayName = "sessionId";

    private final String userId;
    private final String sessionId;

    public ActiveSession(
            @JsonProperty(userIdDisplayName) final String userId,
            @JsonProperty(sessionIdDisplayName) final String sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(userId)
                .addValue(sessionId)
                .omitNullValues()
                .toString();
    }
}
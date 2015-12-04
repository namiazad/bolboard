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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActiveSession that = (ActiveSession) o;

        if (userId != null ? !userId.equals(that.userId) : that.userId != null) return false;
        return !(sessionId != null ? !sessionId.equals(that.sessionId) : that.sessionId != null);
    }

    @Override
    public int hashCode() {
        int result = userId != null ? userId.hashCode() : 0;
        result = 31 * result + (sessionId != null ? sessionId.hashCode() : 0);
        return result;
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
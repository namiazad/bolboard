package model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

public class Principal {
    private final String providerId;
    private final String principalId;
    private final String displayName;
    private final String token;

    @JsonCreator
    public Principal(@JsonProperty("providerId") final String providerId,
                     @JsonProperty("principalId") final String principalId,
                     @JsonProperty("displayName") final String displayName,
                     @JsonProperty("token") final String token) {
        this.providerId = providerId;
        this.principalId = principalId;
        this.displayName = displayName;
        this.token = token;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getToken() {
        return token;
    }

    public String buildUsername() {
        return String.format("%s:%s", providerId, principalId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(providerId)
                .addValue(principalId)
                .addValue(displayName)
                .omitNullValues()
                .toString();
    }
}

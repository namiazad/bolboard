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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Principal principal = (Principal) o;

        if (providerId != null ? !providerId.equals(principal.providerId) : principal.providerId != null) return false;
        if (principalId != null ? !principalId.equals(principal.principalId) : principal.principalId != null)
            return false;
        if (displayName != null ? !displayName.equals(principal.displayName) : principal.displayName != null)
            return false;
        return !(token != null ? !token.equals(principal.token) : principal.token != null);
    }

    @Override
    public int hashCode() {
        int result = providerId != null ? providerId.hashCode() : 0;
        result = 31 * result + (principalId != null ? principalId.hashCode() : 0);
        result = 31 * result + (displayName != null ? displayName.hashCode() : 0);
        result = 31 * result + (token != null ? token.hashCode() : 0);
        return result;
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

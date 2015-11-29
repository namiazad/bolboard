package server;

import com.fasterxml.jackson.databind.JsonNode;
import model.Principal;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

public class FacebookTokenVerifier extends TokenVerifier {
    public static final String PROVIDER_ID = "facebook";
    private static final String PRINCIPAL_ID_JSON_KEY_NAME = "id";
    private static final String VALIDATION_URL = "https://graph.facebook.com/me";
    private static final String ACCESS_TOKEN_QP = "access_token";

    private final WSClient client;

    public FacebookTokenVerifier(final WSClient client) {
        this.client = client;
    }

    @Override
    protected WSClient getHttpClient() {
        return client;
    }

    @Override
    protected WSRequest validationUrl(final WSClient client, final Principal principal) {
        return client.url(VALIDATION_URL).setQueryParameter(ACCESS_TOKEN_QP, principal.getToken());
    }

    @Override
    protected boolean isTokenValid(final WSResponse response, final Principal principal) {
        final JsonNode idNode = response.asJson().get(PRINCIPAL_ID_JSON_KEY_NAME);

        final boolean result;
        if (idNode != null) {
            final String id = idNode.asText();
            result = principal.getPrincipalId().equals(id) && principal.getProviderId().equals(PROVIDER_ID);
        } else {
            result = false;
        }

        return result;
    }
}
package server;

import model.Principal;
import play.libs.F.Promise;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;

/**
 * Verifies the OAuth token.
 */
public abstract class TokenVerifier {
    protected abstract WSClient getHttpClient();
    protected abstract WSRequest validationUrl(final WSClient client, final Principal principal);
    protected abstract boolean isTokenValid(final WSResponse response, final Principal principal);

    public Promise<Boolean> verfiy(final Principal principal) {
        final WSClient client = getHttpClient();
        final WSRequest request = validationUrl(client, principal);

        return request.execute().map(response -> isTokenValid(response, principal));
    }

    public static Promise<Boolean> verify(final WSClient client,
                                          final Principal principal) {
        switch (principal.getProviderId()) {
            case FacebookTokenVerifier.PROVIDER_ID:
                return new FacebookTokenVerifier(client).verfiy(principal);
            default:
        }       return Promise.pure(false);
    }
}

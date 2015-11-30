package server.actors;

import akka.actor.ActorRef;
import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import akka.japi.Util;
import model.ActiveSession;
import model.MessageProtocols;
import model.Principal;
import model.User;
import play.libs.F;
import play.libs.ws.WSClient;
import scala.concurrent.Future;
import server.TokenVerifier;

import java.util.UUID;

import static akka.pattern.Patterns.ask;
import static utils.ExceptionUtils.withCause;

/**
 * This short-living flow is responsible for creating users and their sessions. So upon receive a principal,
 * first its validate its token with the corresponding token verifier. If it is fine, it persists the user and also
 * adds it session in a in-memory bucket.
 */
public class CreateSessionFlow extends UntypedActor {
    final WSClient client;
    final ActorRef sessionStore;
    final int stepTimeout;


    public CreateSessionFlow(final WSClient client,
                             final ActorRef sessionStore,
                             final int stepTimeout) {
        this.client = client;
        this.sessionStore = sessionStore;
        this.stepTimeout = stepTimeout;
    }


    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof Principal) {
            getContext().become(processing);
            handlePrincipal((Principal) message, getSender());
        }
    }

    private Procedure<Object> processing = object -> {
        //while the actor processes the first principal messages, it does not respond to any other message.
    };

    //TODO: e-bean is blocking. It would be ideal to use non-blocking persistence layer.
    protected User persistUser(final Principal principal) {
        try {
            log.debug("User is being created (or updated) out of principal {}.", principal);

            final String username = principal.buildUsername();
            final User searchResult = User.findByUserName(username);

            final User user;
            if (searchResult == null) {
                user = new User(username, principal.getDisplayName(), true);
                log.debug("User {} is new and being created.", user);
                user.insert();
            } else {
                user = searchResult.copy().online(true).build();
                log.debug("User {} is new and being created.", user);
                user.update();
            }

            return user;
        } catch (final RuntimeException exception) {
            log.error(exception, "Persisting user {} failed due to: ", principal.buildUsername());
            throw withCause(new MessageProtocols.Exceptions.UserCreationException(), exception);
        }
    }

    /**
     * Validates the oauth token, stores the user and creates a new session for the user. If everything is
     * successful, it replies the sender with an {@link ActiveSession} instance. It stops the actor after it
     * finishes its task regardless of success or failure.
     *
     * @param principal the user principal.
     * @param responder the responder actor to which the responses should be sent.
     */
    private void handlePrincipal(final Principal principal,
                                 final ActorRef responder) {
        F.Promise<ActiveSession> sessionPromise = TokenVerifier.verify(client, principal)
                .fallbackTo(F.Promise.pure(false))
                .map(isValid -> {
                    if (isValid) {
                        log.debug("Provided OAuth token for user {} was valid. User is persisting...", principal.buildUsername());
                        return persistUser(principal);
                    }
                    log.debug("Provided OAuth token for user {} was not valid!", principal.buildUsername());
                    throw new MessageProtocols.Exceptions.InvalidTokenException();
                })
                .flatMap(user -> {
                    final String sessionId = UUID.randomUUID().toString();
                    final SessionInMemoryStore.CacheSession cacheSessionCommand = new SessionInMemoryStore.CacheSession(
                            new ActiveSession(user.getUserId(), sessionId)
                    );

                    Future<ActiveSession> activeSessionFuture =
                            ask(sessionStore, cacheSessionCommand, stepTimeout).mapTo(Util.classTag(ActiveSession.class));
                    return F.Promise.wrap(activeSessionFuture);
                });

        sessionPromise.onFailure(throwable -> {
            responder.tell(new Status.Failure(throwable), self());
            getContext().stop(self());
        });

        sessionPromise.onRedeem(activeSession -> {
            responder.tell(activeSession, self());
            getContext().stop(self());
        });
    }
}

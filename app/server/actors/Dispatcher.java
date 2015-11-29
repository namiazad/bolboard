package server.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Util;
import model.ActiveSession;
import model.Principal;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WSClient;

import java.util.UUID;

import static akka.pattern.Patterns.ask;
import static play.mvc.Results.Status;
import static play.mvc.Results.internalServerError;
import static play.mvc.Results.ok;
import static play.mvc.Results.unauthorized;

/**
 * The singleton actor which acts as the parent of all the short living actors.
 */
public class Dispatcher extends UntypedActor {
    private final static String DEFAULT_CHARSET = "UTF-8";

    final WSClient client;
    final ActorRef sessionStore;
    final int stepTimeout;
    final int flowTimeout;

    public Dispatcher(final WSClient client,
                      final ActorRef sessionStore,
                      final int stepTimeout,
                      final int flowTimeout) {
        this.client = client;
        this.sessionStore = sessionStore;
        this.stepTimeout = stepTimeout;
        this.flowTimeout = flowTimeout;
    }

    // ==========================================================================
    // Dispatcher message protocols
    // ==========================================================================
    public static class CreateSession {
        private final Principal principal;

        public CreateSession(final Principal principal) {
            this.principal = principal;
        }

        public Principal getPrincipal() {
            return principal;
        }
    }

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    // ==========================================================================
    // Helper functions to dispatch the request to the short living actor
    // ==========================================================================
    private void handlerCreateSession(final CreateSession createSession, final ActorRef responder) {
        log.debug("Creating session for user {} has been dispatched", createSession.getPrincipal().buildUsername());

        ActorRef createSessionFlow = createSessionFlowActor();

        F.Promise<ActiveSession> sessionPromise = F.Promise.wrap(
                ask(createSessionFlow, createSession.getPrincipal(), flowTimeout)
                        .mapTo(Util.classTag(ActiveSession.class)));

        sessionPromise.onFailure(throwable -> {
            final Status result;
            if (throwable instanceof CreateSessionFlow.InvalidTokenException) {
                result = unauthorized("Provided OAuth token is not valid!", DEFAULT_CHARSET);
            } else {
                result = internalServerError("Unknown failure", DEFAULT_CHARSET);
            }
            responder.tell(result, self());
        });

        sessionPromise.onRedeem(activeSession -> responder.tell(ok(Json.toJson(activeSession)), self()));
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof CreateSession) {
            handlerCreateSession((CreateSession) message, getSender());
        }
    }

    // ==========================================================================
    // Factory methods to create the short living actors
    // ==========================================================================
    protected ActorRef createSessionFlowActor() {
        return getContext().actorOf(
                Props.create(CreateSessionFlow.class, client, sessionStore, stepTimeout),
                String.format("create-session-flow-%s", UUID.randomUUID().toString()));
    }
}

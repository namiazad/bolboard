package server.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Util;
import com.rabbitmq.client.Connection;
import model.ActiveSession;
import model.Principal;
import model.SearchResult;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.mvc.Http;

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
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final static String DEFAULT_CHARSET = "UTF-8";

    final WSClient client;
    final Connection mqConnection;
    final ActorRef sessionStore;
    final int stepTimeout;
    final int flowTimeout;

    public Dispatcher(final WSClient client,
                      final Connection mqConnection,
                      final ActorRef sessionStore,
                      final int stepTimeout,
                      final int flowTimeout) {
        this.client = client;
        this.mqConnection = mqConnection;
        this.sessionStore = sessionStore;
        this.stepTimeout = stepTimeout;
        this.flowTimeout = flowTimeout;
    }

    // ==========================================================================
    // Dispatcher message protocols
    // ==========================================================================
    public static class CreateSession {
        private final Principal principal;
        private final Http.Context requestContext;

        public CreateSession(final Principal principal,
                             final Http.Context requestContext) {
            this.principal = principal;
            this.requestContext = requestContext;
        }

        public Principal getPrincipal() {
            return principal;
        }

        public Http.Context getRequestContext() {
            return requestContext;
        }
    }

    public static class Search {
        private final String content;
        private final ActiveSession session;

        public Search(ActiveSession session, String content) {
            this.session = session;
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public ActiveSession getSession() {
            return session;
        }
    }

    public static class GameRequest {
        public static final String MQ_GAME_REQUEST_PREFIX = "game_request";

        private final ActiveSession requester;
        private final String target;

        public GameRequest(ActiveSession requester, String target) {
            this.requester = requester;
            this.target = target;
        }

        public ActiveSession getRequester() {
            return requester;
        }

        public String getTarget() {
            return target;
        }

        public String buildMQMessage() {
            return String.format("%s=%s", MQ_GAME_REQUEST_PREFIX, getRequester().getUserId());
        }
    }

    // ==========================================================================
    // Helper functions to dispatch the request to the short living actor
    // ==========================================================================
    private F.Promise<ActiveSession> authenticate(final ActiveSession session) {
        return F.Promise.wrap(
                ask(sessionStore, new SessionInMemoryStore.LoadSession(session.getUserId()), stepTimeout)
                        .mapTo(Util.classTag(ActiveSession.class)));
    }

    private void handleCreateSession(final CreateSession createSession, final ActorRef responder) {
        log.debug("Creating session for user {} is being dispatched", createSession.getPrincipal().buildUsername());

        final ActorRef createSessionFlow = createSessionFlowActor();

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

        sessionPromise.onRedeem(activeSession -> {
            createSession.getRequestContext().session().put(ActiveSession.userIdDisplayName, activeSession.getUserId());
            createSession.getRequestContext().session().put(ActiveSession.sessionIdDisplayName, activeSession.getSessionId());
            responder.tell(ok(Json.toJson(activeSession)), self());
        });
    }

    private void handleSearch(final Search search, final ActorRef responder) {
        log.debug("Search for user {} is being dispatched", search.getSession().getUserId());

        final ActorRef searchFlow = createSearchFlowActor();

        F.Promise<SearchResult> searchPromise = authenticate(search.session)
                .flatMap(activeSession -> F.Promise.wrap(
                        ask(searchFlow, search, flowTimeout)
                                .mapTo(Util.classTag(SearchResult.class))));

        searchPromise.onFailure(throwable -> {
            final Status result;
            if (throwable instanceof SessionInMemoryStore.UserNotFoundException) {
                result = unauthorized("Provided token is not valid!", DEFAULT_CHARSET);
            } else {
                result = internalServerError("Unknown failure", DEFAULT_CHARSET);
            }
            responder.tell(result, self());
        });

        searchPromise.onRedeem(searchResult -> responder.tell(ok(Json.toJson(searchResult)), self()));
    }

    private void handleGameRequest(final GameRequest gameRequest, final ActorRef responder) {
        log.debug("GameRequest from user {} to play with user {} is being dispatched",
                gameRequest.getRequester().getUserId(), gameRequest.target);

        final ActorRef gameRequestFlow = createGameRequestFlowActor();
        F.Promise<ActiveSession> activeSessionPromise = authenticate(gameRequest.requester);

        activeSessionPromise.onFailure(throwable -> {
            final Status result;
            if (throwable instanceof SessionInMemoryStore.UserNotFoundException) {
                result = unauthorized("Provided token is not valid!", DEFAULT_CHARSET);
            } else {
                result = internalServerError("Unknown failure", DEFAULT_CHARSET);
            }
            responder.tell(result, self());
        });

        activeSessionPromise.onRedeem(activeSession -> {
            gameRequestFlow.tell(gameRequest, self());
            //TODO: Maybe Accepted instead of Ok!
            responder.tell(ok(), self());
        });
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof CreateSession) {
            handleCreateSession((CreateSession) message, getSender());
        } else if (message instanceof Search) {
            handleSearch((Search) message, getSender());
        } else if (message instanceof GameRequest) {
            handleGameRequest((GameRequest) message, getSender());
        }
    }

    // ==========================================================================
    // Factory methods to create the short living actors
    // ==========================================================================
    protected ActorRef createSessionFlowActor() {
        return getContext().actorOf(
                Props.create(CreateSessionFlow.class, client, sessionStore, stepTimeout),
                String.format("create-session-flow-%s", UUID.randomUUID()));
    }

    protected ActorRef createSearchFlowActor() {
        return getContext().actorOf(
                Props.create(SearchFlow.class),
                String.format("search-flow-%s", UUID.randomUUID()));
    }

    protected ActorRef createGameRequestFlowActor() {
        return getContext().actorOf(
                Props.create(GameRequestFlow.class, mqConnection),
                String.format("game-request-%s", UUID.randomUUID()));
    }
}

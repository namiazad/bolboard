package server.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Util;
import com.rabbitmq.client.Connection;
import controllers.Application;
import model.ActiveSession;
import model.MessageProtocols;
import model.SearchResult;
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
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

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


    // ==========================================================================
    // Helper functions to dispatch the request to the short living actor
    // ==========================================================================
    private F.Promise<ActiveSession> authenticate(final ActiveSession session) {
        return F.Promise.wrap(
                ask(sessionStore, new SessionInMemoryStore.LoadSession(session.getUserId()), stepTimeout)
                        .mapTo(Util.classTag(ActiveSession.class)));
    }

    private void handleCreateSession(final MessageProtocols.CreateSession createSession, final ActorRef responder) {
        log.debug("Creating session for user {} is being dispatched", createSession.getPrincipal().buildUsername());

        final ActorRef createSessionFlow = createSessionFlowActor();

        F.Promise<ActiveSession> sessionPromise = F.Promise.wrap(
                ask(createSessionFlow, createSession.getPrincipal(), flowTimeout)
                        .mapTo(Util.classTag(ActiveSession.class)));

        sessionPromise.onFailure(throwable -> {
            final Status result;
            if (throwable instanceof MessageProtocols.Exceptions.InvalidTokenException) {
                result = unauthorized("Provided OAuth token is not valid!", Application.DEFAULT_CHARSET);
            } else {
                result = internalServerError("Unknown failure", Application.DEFAULT_CHARSET);
            }
            responder.tell(result, self());
        });

        sessionPromise.onRedeem(activeSession -> {
            createSession.getRequestContext().session().put(ActiveSession.userIdDisplayName, activeSession.getUserId());
            createSession.getRequestContext().session().put(ActiveSession.sessionIdDisplayName, activeSession.getSessionId());
            responder.tell(ok(Json.toJson(activeSession)), self());
        });
    }

    private void handleSearch(final MessageProtocols.Search search, final ActorRef responder) {
        log.debug("Search for user {} is being dispatched", search.getSession().getUserId());

        final ActorRef searchFlow = createSearchFlowActor();

        F.Promise<SearchResult> searchPromise = authenticate(search.getSession())
                .flatMap(activeSession -> F.Promise.wrap(
                        ask(searchFlow, search, flowTimeout)
                                .mapTo(Util.classTag(SearchResult.class))));

        searchPromise.onFailure(throwable -> {
            final Status result;
            if (throwable instanceof SessionInMemoryStore.UserNotFoundException) {
                result = unauthorized("Provided token is not valid!", Application.DEFAULT_CHARSET);
            } else {
                result = internalServerError("Unknown failure", Application.DEFAULT_CHARSET);
            }
            responder.tell(result, self());
        });

        searchPromise.onRedeem(searchResult -> responder.tell(ok(Json.toJson(searchResult)), self()));
    }

    private void handleGameRequest(final MessageProtocols.GameRequest gameRequest, final ActorRef responder) {
        log.debug("GameRequest from user {} to play with user {} is being dispatched",
                gameRequest.getRequester().getUserId(), gameRequest.getTarget());

        final ActorRef gameRequestFlow = createGameRequestFlowActor();
        F.Promise<ActiveSession> activeSessionPromise = authenticate(gameRequest.getRequester());

        activeSessionPromise.onFailure(throwable -> {
            final Status result;
            if (throwable instanceof SessionInMemoryStore.UserNotFoundException) {
                result = unauthorized("Provided token is not valid!", Application.DEFAULT_CHARSET);
            } else {
                result = internalServerError("Unknown failure", Application.DEFAULT_CHARSET);
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
        if (message instanceof MessageProtocols.CreateSession) {
            handleCreateSession((MessageProtocols.CreateSession) message, getSender());
        } else if (message instanceof MessageProtocols.Search) {
            handleSearch((MessageProtocols.Search) message, getSender());
        } else if (message instanceof MessageProtocols.GameRequest) {
            handleGameRequest((MessageProtocols.GameRequest) message, getSender());
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

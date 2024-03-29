package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Util;
import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.ConnectionFactory;
import model.ActiveSession;
import model.MessageProtocols;
import model.Principal;
import play.Logger;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSClient;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.WebSocket;
import server.actors.Dispatcher;
import server.actors.SessionInMemoryStore;
import server.actors.SocketHandler;
import utils.SafeChannel;
import views.html.index;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.IOException;

import static akka.pattern.Patterns.ask;
import static utils.SafeChannel.managed;

@Singleton
public class Application extends Controller {

    //TODO: makes the timeouts configurable
    private final static int STEP_TIMEOUT = 3000;
    private final static int FLOW_TIMEOUT = 6000;
    private final static int DISPATCH_TIMEOUT = 12000;

    public final static String RabbitMQExchangeName = "BOL";
    public final static String DEFAULT_CHARSET = "UTF-8";

    private final ActorRef dispatcher;
    private final ActorRef sessionStore;
    private Connection connection;

    @Nullable
    private ActiveSession loadSession() {
        final String userId = session(ActiveSession.userIdDisplayName);
        final String sessionId = session(ActiveSession.sessionIdDisplayName);

        final ActiveSession session;
        if (userId == null || sessionId == null) {
            session = null;
        } else {
            session = new ActiveSession(userId, sessionId);
        }

        return session;
    }

    private F.Promise<Result> dispatch(final Object command) {
        return F.Promise.wrap(
                ask(dispatcher, command, DISPATCH_TIMEOUT)
                        .mapTo(Util.classTag(Result.class)));
    }

    @Inject
    public Application(final ActorSystem system) {
        final WSClient client = WS.client();
        sessionStore = system.actorOf(Props.create(SessionInMemoryStore.class));

        final ConnectionFactory factory = new ConnectionFactory();

        //TODO: should be read from the config file
        factory.setHost("localhost");
        factory.setPort(5672);



        try {
            connection = factory.newConnection();

            managed(connection, Connection::createChannel, new SafeChannel.CheckedFunction<Channel, Void>() {
                @Override
                public Void apply(Channel channel) throws IOException {
                    channel.exchangeDeclare(RabbitMQExchangeName, "direct", true);
                    return null;
                }
            });

            dispatcher = system.actorOf(Props.create(Dispatcher.class,
                    client, connection, sessionStore, STEP_TIMEOUT, FLOW_TIMEOUT));
        } catch (final IOException e) {
            connection = null;
            Logger.error("Connection to RabbitMQ failed due to: ", e);
            throw new RuntimeException(e);
        }

    }

    public Result index() {
        return ok(index.render("Welcome to BolBoard!"));
    }

    @BodyParser.Of(BodyParser.Json.class)
    public F.Promise<Result> createSession() {
        final JsonNode json = request().body().asJson();

        //TODO: wrap it in try catch so if the json is not valid principal 400 will be returned.
        final Principal principal = Json.fromJson(json, Principal.class);

        final MessageProtocols.CreateSession createSessionCommand = new MessageProtocols.CreateSession(principal, Http.Context.current());
        return dispatch(createSessionCommand);
    }

    public WebSocket<String> socket() {
        return new WebSocket<String>() {
            public void onReady(In<String> in, Out<String> out) {
            }

            public boolean isActor() {
                return true;
            }

            public Props actorProps(final ActorRef out) {
                try {
                    return SocketHandler.props(out, sessionStore, connection);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        };
    }

    @BodyParser.Of(BodyParser.Text.class)
    public F.Promise<Result> search() {
        final ActiveSession session = loadSession();
        if (session == null) {
            return F.Promise.pure(unauthorized());
        } else {
            final MessageProtocols.Search searchCommand = new MessageProtocols.Search(session, request().body().asText());
            return dispatch(searchCommand);
        }
    }

    //The body of the request is a plain text containing opponent user id.
    @BodyParser.Of(BodyParser.Text.class)
    public F.Promise<Result> gameRequest() {
        final ActiveSession session = loadSession();
        if (session == null) {
            return F.Promise.pure(unauthorized());
        } else {
            final MessageProtocols.GameRequest gameRequest = new MessageProtocols.GameRequest(session, request().body().asText());
            return dispatch(gameRequest);
        }
    }
}

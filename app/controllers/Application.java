package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Util;
import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.ConnectionFactory;
import model.ActiveSession;
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
import views.html.index;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.IOException;

import static akka.pattern.Patterns.ask;

@Singleton
public class Application extends Controller {

    //TODO: makes the timeouts configurable
    private final static int STEP_TIMEOUT = 3000;
    private final static int FLOW_TIMEOUT = 6000;
    private final static int DISPATCH_TIMEOUT = 12000;

    private final static String RabbitMQExchangeName = "BOL";

    private final ActorRef dispatcher;

    @Inject
    public Application(final ActorSystem system) {
        final WSClient client = WS.client();
        final ActorRef sessionStore = system.actorOf(Props.create(SessionInMemoryStore.class));

        final ConnectionFactory factory = new ConnectionFactory();

        //TODO: should be read from the config file
        factory.setHost("localhost");
        factory.setPort(5672);

        try {
            final Connection connection = factory.newConnection();

            final Channel channel = connection.createChannel();
            channel.exchangeDeclare(RabbitMQExchangeName, "direct", true);
            channel.close();

            dispatcher = system.actorOf(Props.create(Dispatcher.class,
                client, connection, sessionStore, STEP_TIMEOUT, FLOW_TIMEOUT));

        } catch (final IOException e) {
            Logger.error("Connection to RabbitMQ failed due to: ", e);
            throw new RuntimeException(e);
        }

    }

    public Result index() {
        return ok(index.render("Welcome to Bo   lBoard!"));
    }

    @BodyParser.Of(BodyParser.Json.class)
    public F.Promise<Result> createSession() {
        final JsonNode json = request().body().asJson();

        //TODO: wrap it in try catch so if the json is not valid principal 400 will be returned.
        final Principal principal = Json.fromJson(json, Principal.class);

        final Dispatcher.CreateSession createSessionCommand = new Dispatcher.CreateSession(principal, Http.Context.current());
        return F.Promise.wrap(
                ask(dispatcher, createSessionCommand, DISPATCH_TIMEOUT)
                        .mapTo(Util.classTag(Result.class)));
    }

    public WebSocket<String> socket() {
        return WebSocket.withActor(SocketHandler::props);
    }

    //TODO: makes all the endpoints accepting just JSON
    @BodyParser.Of(BodyParser.Text.class)
    public F.Promise<Result> search() {
        final String userId = session(ActiveSession.userIdDisplayName);
        final String sessionId = session(ActiveSession.sessionIdDisplayName);

        if (userId == null || sessionId == null) {
            return F.Promise.pure(unauthorized());
        } else {
            final ActiveSession session = new ActiveSession(userId, sessionId);
            final Dispatcher.Search searchCommand = new Dispatcher.Search(session, request().body().asText());

            return F.Promise.wrap(
                    ask(dispatcher, searchCommand, DISPATCH_TIMEOUT)
                            .mapTo(Util.classTag(Result.class)));
        }
    }

    @BodyParser.Of(BodyParser.Text.class)
    public F.Promise<Result> gameRequest() {
        return F.Promise.pure(ok());
    }
}

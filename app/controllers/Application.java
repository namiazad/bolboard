package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Util;
import com.fasterxml.jackson.databind.JsonNode;
import model.Principal;
import play.Logger;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSClient;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import server.actors.Dispatcher;
import server.actors.SessionInMemoryStore;
import views.html.index;

import javax.inject.Inject;
import javax.inject.Singleton;

import static akka.pattern.Patterns.ask;

@Singleton
public class Application extends Controller {

    //TODO: makes the timeouts configurable
    private final static int STEP_TIMEOUT = 3000;
    private final static int FLOW_TIMEOUT = 6000;
    private final static int DISPATCH_TIMEOUT = 12000;

    private final ActorRef dispatcher;

    @Inject
    public Application(final ActorSystem system) {
        final WSClient client = WS.client();
        final ActorRef sessionStore = system.actorOf(Props.create(SessionInMemoryStore.class));

        dispatcher = system.actorOf(Props.create(Dispatcher.class, client, sessionStore, STEP_TIMEOUT, FLOW_TIMEOUT));
    }

    public Result index() {
        return ok(index.render("Welcome to Bo   lBoard!"));
    }

    @BodyParser.Of(BodyParser.Json.class)
    public F.Promise<Result> createSession() {
        final JsonNode json = request().body().asJson();

        //TODO: wrap it in try catch so if the json is not valid principal 400 will be returned.
        final Principal principal = Json.fromJson(json, Principal.class);

        final Dispatcher.CreateSession createSession = new Dispatcher.CreateSession(principal);

        return F.Promise.wrap(
                ask(dispatcher, createSession, DISPATCH_TIMEOUT)
                        .mapTo(Util.classTag(Result.class)));
    }
}

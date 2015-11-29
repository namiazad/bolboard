package server.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.fasterxml.jackson.databind.JsonNode;

public class SocketHandler extends UntypedActor {
    private final ActorRef out;

    public static Props props(ActorRef out) {
        return Props.create(SocketHandler.class, out);
    }

    public SocketHandler(final ActorRef out) {
        this.out = out;
    }

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof String) {
            log.debug("Message {} received! " + out.toString(), message.toString());
            out.tell("{'name' : 'mkyong'}", self());
            //TODO: handle websocket message
        }
    }
}

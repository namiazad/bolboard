package server.actors;

import akka.actor.ActorRef;
import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import controllers.Application;

import java.io.IOException;

public class GameRequestFlow extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    final Connection connection;

    public GameRequestFlow(final Connection connection) {
        this.connection = connection;
    }

    private void handleGameRequest(final Dispatcher.GameRequest gameRequest, final ActorRef responder) {
        Channel channel = null;
        try {
            channel = connection.createChannel();

            final String message = gameRequest.buildMQMessage();
            log.debug("Message {} published to MQ with routing key {}.", message, gameRequest.getTarget());
            channel.basicPublish(Application.RabbitMQExchangeName,
                    gameRequest.getTarget(),
                    new AMQP.BasicProperties.Builder()
                            .contentType("text/plain").deliveryMode(1)
                            .build(),
                    message.getBytes(Application.DEFAULT_CHARSET));
        } catch (final IOException e) {
            log.error(e, "Opening channel to RabbitMQ failed!");
            responder.tell(new Status.Failure(new RuntimeException(e)), self());
        } finally {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException e) {
                    //DO NOTHING
                }
            }
        }
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof Dispatcher.GameRequest) {
            getContext().become(processing);
            handleGameRequest((Dispatcher.GameRequest) message, getSender());
        }
    }

    private Procedure<Object> processing = object -> {
        //while the actor processes the first game request, it does not respond to any other message.
    };
}

package server.actors;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import controllers.Application;
import model.MessageProtocols;
import utils.SafeChannel;

import static utils.SafeChannel.managed;

public class GameRequestFlow extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    final Connection connection;

    public GameRequestFlow(final Connection connection) {
        this.connection = connection;
    }

    private void handleGameRequest(final MessageProtocols.GameRequest gameRequest) {
        managed(connection, Connection::createChannel, (SafeChannel.CheckedFunction<Channel, Void>) channel -> {
            final String message = gameRequest.buildMQMessage();
            log.debug("Message {} published to MQ with routing key {}.", message, gameRequest.getTarget());
            channel.basicPublish(Application.RabbitMQExchangeName,
                    gameRequest.getTarget(),
                    new AMQP.BasicProperties.Builder()
                            .contentType("text/plain").deliveryMode(1)
                            .build(),
                    message.getBytes(Application.DEFAULT_CHARSET));
            return null;
        });
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof MessageProtocols.GameRequest) {
            getContext().become(processing);
            handleGameRequest((MessageProtocols.GameRequest) message);
        }
    }

    private Procedure<Object> processing = object -> {
        //while the actor processes the first game request, it does not respond to any other message.
    };
}

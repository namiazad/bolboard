package server.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import controllers.Application;
import model.ActiveSession;

import java.io.IOException;
import java.util.UUID;

public class SocketHandler extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final ActorRef out;

    private final ActorRef sessionStore;
    private final Connection connection;

    private Channel consumingChannel;

    public static Props props(final ActorRef out,
                              final ActorRef sessionStore,
                              final Connection connection) {
        return Props.create(SocketHandler.class, out, sessionStore, connection);
    }

    public SocketHandler(final ActorRef out,
                         final ActorRef sessionStore,
                         final Connection connection) {
        this.out = out;
        this.sessionStore = sessionStore;
        this.connection = connection;
    }

    private ActiveSession parseMessageToActiveSession(final String message) {
        final String[] parts = message.split("=");

        final ActiveSession session;
        if (parts.length != 2) {
            session = null;
        } else {
            session = new ActiveSession(parts[0], parts[1]);
        }

        return session;
    }

    private String createQueueName(final ActiveSession session) {
        return String.format("%s-%s", session.getUserId().replace(":", "-"), UUID.randomUUID());
    }

    private void consumeQueue(final String queueName) {
        try {
            consumingChannel = connection.createChannel();

            final boolean autoAck = false;
            consumingChannel.basicConsume(queueName, autoAck, new DefaultConsumer(consumingChannel) {
                @Override
                public void handleDelivery(final String consumerTag,
                                           final Envelope envelope,
                                           final AMQP.BasicProperties properties,
                                           final byte[] body) throws IOException {
//                    String routingKey = envelope.getRoutingKey();
//                    String contentType = properties.getContentType();

                    log.debug("A message has been received from MQ!");
                    final String deliveredMessage = new String(body, Application.DEFAULT_CHARSET);
                    long deliveryTag = envelope.getDeliveryTag();
                    out.tell(deliveredMessage, self());
                    consumingChannel.basicAck(deliveryTag, false);
                }
            });
        } catch (final IOException e) {
            log.error(e, "Channel creation failed due to: ");
            throw new RuntimeException(e);
        }
    }

    private String createQueue(final ActiveSession session) {
        //TODO: creating queue and binding to that
        Channel creationChannel = null;
        try {
            creationChannel = connection.createChannel();
            final Channel scopedChannel = creationChannel;
            final String queueName = createQueueName(session);
            final boolean durable = false;
            final boolean exclusive = false;
            final boolean autoDelete = true;

            final String createdQueue = scopedChannel
                    .queueDeclare(queueName, durable, exclusive, autoDelete, null)
                    .getQueue();

            log.debug("Queue {} has been created!", createdQueue);

            scopedChannel.queueBind(createdQueue, Application.RabbitMQExchangeName, session.getUserId());

            log.debug("Queue {} and exchange {} are bound with {}!",
                    createdQueue, Application.RabbitMQExchangeName, session.getSessionId());

            return createdQueue;
        } catch (final IOException e) {
            log.error(e, "Channel creation failed due to: ");
            throw new RuntimeException(e);
        } finally {
            if (creationChannel != null && creationChannel.isOpen()) {
                try {
                    creationChannel.close();
                } catch (IOException e) {
                    //Do Nothing
                }
            }
        }
    }

    private void handleCredentials(final String message) {
        final ActiveSession session = parseMessageToActiveSession(message);

        log.debug("Active Session {} received as first message of socket connection", session);

        if (session == null) {
            self().tell(new Status.Failure(new SessionInMemoryStore.UserNotFoundException()), self());
        } else {
            sessionStore.tell(new SessionInMemoryStore.LoadSession(session.getUserId()), self());
        }
    }

    private Procedure<Object> afterAuthentication = object -> {

    };

    private Procedure<Object> waitForAuthentication = message -> {
        if (message instanceof ActiveSession) {
            try {
                log.debug("Authentication has been done successfully!");
                final ActiveSession session = (ActiveSession) message;
                consumeQueue(createQueue(session));
                getContext().become(afterAuthentication);
            } catch (final Exception e) {
                log.error(e, "Queue creation and consumption went wrong!");
                getContext().stop(self());
            }
        } else {
            log.debug("Unknown message {} received!", message);
            getContext().stop(self());
        }
    };

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof String) {
            log.debug("Message {} received! " + out.toString(), message.toString());
            getContext().become(waitForAuthentication);
            handleCredentials((String) message);
            //TODO: handle websocket message
        }
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();

        if (consumingChannel != null && consumingChannel.isOpen()) {
            consumingChannel.close();
        }

        //TODO: makes user offline

        log.debug("Socket Handler has been killed!");
    }

}

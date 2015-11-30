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
import model.MessageProtocols;

import java.io.IOException;
import java.util.UUID;

import static utils.SafeChannel.managed;

public class SocketHandler extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final ActorRef out;

    private final ActorRef sessionStore;
    private final Connection connection;

    private Channel consumingChannel;

    private boolean playing = false;

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

    // ==========================================================================
    // Implementation details
    // ==========================================================================

    /**
     * Parses the initial message coming via web socket to active session
     *
     * @param message the initial message
     * @return an active session if it has correct format otherwise null;
     */
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

    /**
     * A helper function to create the rabbitMQ queue name out of the session
     *
     * @param session user session
     * @return
     */
    private String createQueueName(final ActiveSession session) {
        return String.format("%s-%s", session.getUserId().replace(":", "-"), UUID.randomUUID());
    }

    /**
     * Subscribes to the created queue to consume events
     *
     * @param queueName the queue to subscribe to
     */
    private void consumeQueue(final String queueName) {
        final boolean keepChannelOpen = true;
        consumingChannel = managed(connection, Connection::createChannel, channel -> {
            final boolean autoAck = false;
            channel.basicConsume(queueName, autoAck, new DefaultConsumer(channel) {
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
                    channel.basicAck(deliveryTag, false);
                }
            });
            return channel;
        }, keepChannelOpen);
    }

    /**
     * Creates a rabbitMQ queue to receive the events
     *
     * @param session the active session
     * @return the created queue name
     */
    private String createQueue(final ActiveSession session) {
        return managed(connection, Connection::createChannel, channel -> {
            final String queueName = createQueueName(session);
            final boolean durable = false;
            final boolean exclusive = false;
            final boolean autoDelete = true;

            final String createdQueue = channel
                    .queueDeclare(queueName, durable, exclusive, autoDelete, null)
                    .getQueue();

            log.debug("Queue {} has been created!", createdQueue);

            channel.queueBind(createdQueue, Application.RabbitMQExchangeName, session.getUserId());

            log.debug("Queue {} and exchange {} are bound with {}!",
                    createdQueue, Application.RabbitMQExchangeName, session.getSessionId());

            return createdQueue;
        });
    }

    /**
     * handled game request
     *
     * @param gameRequest the message containing the game request
     */
    private void handleGameRequest(final String gameRequest) {
        final String requester = MessageProtocols.GameRequest.fetchRequester(gameRequest);


    }

    /**
     * verifies the first message coming via web-socket is a valid active session
     *
     * @param message the web-socket message
     */
    private void handleCredentials(final String message) {
        final ActiveSession session = parseMessageToActiveSession(message);

        log.debug("Active Session {} received as first message of socket connection", session);

        if (session == null) {
            self().tell(new Status.Failure(new SessionInMemoryStore.UserNotFoundException()), self());
        } else {
            sessionStore.tell(new SessionInMemoryStore.LoadSession(session.getUserId()), self());
        }
    }

    // ==========================================================================
    // Receive partial functions for different states of the actor
    // ==========================================================================
    private Procedure<Object> waitForGameStart = message -> {
        if (message instanceof String) {

        }
    };

    private Procedure<Object> waitForGameRequest = message -> {
        if (message instanceof String) {
            final String str = (String) message;
            if (MessageProtocols.GameRequest.isGameRequest(str)) {
                getContext().become(waitForGameStart);
                handleGameRequest(str);
            }
        }
    };

    private Procedure<Object> waitForAuthentication = message -> {
        if (message instanceof ActiveSession) {
            try {
                log.debug("Authentication has been done successfully!");
                final ActiveSession session = (ActiveSession) message;
                consumeQueue(createQueue(session));
                getContext().become(waitForGameRequest);
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


    // ==========================================================================
    // Actor hooks
    // ==========================================================================
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

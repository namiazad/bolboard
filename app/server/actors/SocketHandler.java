package server.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
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
import model.User;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static utils.SafeChannel.managed;

public class SocketHandler extends UntypedActor {
    private final static FiniteDuration GAME_START_TIMEOUT = new FiniteDuration(15, TimeUnit.SECONDS);
    private final static FiniteDuration GAME_MOVEMENT_TIMEOUT = new FiniteDuration(60, TimeUnit.SECONDS);

    public static Props props(final ActorRef out,
                              final ActorRef sessionStore,
                              final Connection connection) {
        return Props.create(SocketHandler.class, out, sessionStore, connection);
    }

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final ActorRef out;

    private final ActorRef sessionStore;
    private final Connection connection;

    private Channel consumingChannel;
    private ActiveSession session;
    private String opponentUserId;
    private boolean playing = false;


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
                    final String deliveredMessage = new String(body, Application.DEFAULT_CHARSET);
                    log.debug("A message has been received from MQ: {}", deliveredMessage);

                    long deliveryTag = envelope.getDeliveryTag();

                    if (!playing
                            || MessageProtocols.GameRequest.isGameInstructionMessage(deliveredMessage)
                            || MessageProtocols.GameRequest.isGameRejectedMessage(deliveredMessage)) {
                        self().tell(deliveredMessage, self());
                    } else {
                        log.debug("MQ message {} has been discarded for user {}, playing status: {}",
                                deliveredMessage, session.getUserId(), playing);
                    }

                    channel.basicAck(deliveryTag, false);
                }
            });
            return channel;
        }, keepChannelOpen);
        getContext().become(waitForGameRequestOrAccept);
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

    private void publishMessage(final String message, final String routingKey) {
        managed(connection, Connection::createChannel, channel -> {
            if (session != null) {
                channel.basicPublish(Application.RabbitMQExchangeName,
                        routingKey,
                        new AMQP.BasicProperties.Builder()
                                .contentType("text/plain").deliveryMode(1)
                                .build(),
                        message.getBytes(Application.DEFAULT_CHARSET));
            }
            return null;
        });
    }

    private void waitForGameRequest() {
        log.debug("User {} changed the state to waiting for a game request!", session.getUserId());
        playing = false;
        opponentUserId = null;

        out.tell(MessageProtocols.GameRequest.buildSocketWaitingForRequestMessage(), self());

        getContext().setReceiveTimeout(FiniteDuration.Undefined());
        getContext().become(waitForGameRequestOrAccept);
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
            this.session = session;
            sessionStore.tell(new SessionInMemoryStore.LoadSession(session.getUserId()), self());
        }
    }

    /**
     * handled game request
     *
     * @param gameRequest the message containing the game request
     */
    private void handleGameRequest(final String gameRequest) {
        final String requester = MessageProtocols.GameRequest.fetchRequester(gameRequest);
        final String acceptedMessage = MessageProtocols.GameRequest.buildAcceptMessage(session.getUserId());

        publishMessage(acceptedMessage, requester);
        log.debug("Game request from user {} accepted by user {}.", requester, session.getUserId());
        getContext().setReceiveTimeout(GAME_START_TIMEOUT);
        getContext().become(waitForGameStart);
    }

    private void handleGameAccepted(final String accept) {
        final String opponent = MessageProtocols.GameRequest.fetchAccepter(accept);
        final String gameStartMessage = MessageProtocols.GameRequest.buildStartMessage(session.getUserId());
        publishMessage(gameStartMessage, opponent);
        startGame(opponent);
    }

    private void handleGameStart(final String gameStart) {
        final String opponent = MessageProtocols.GameRequest.fetchStarter(gameStart);
        startGame(opponent);
    }

    private void startGame(final String opponentUserId) {
        this.opponentUserId = opponentUserId;
        this.playing = true;
        log.debug("Game between user {} and {} started.", session.getUserId(), this.opponentUserId);

        final User opponent = User.findByUserId(opponentUserId);
        if (opponent != null) {
            out.tell(MessageProtocols.GameRequest.buildSocketGameStartMessage(opponent.getDisplayName()), self());
        } else {
            waitForGameRequest();
        }

        getContext().setReceiveTimeout(GAME_MOVEMENT_TIMEOUT);
        getContext().become(gaming);
    }

    // ==========================================================================
    // Receive partial functions for different states of the actor
    // ==========================================================================
    private Procedure<Object> waitForAuthentication = message -> {
        if (message instanceof ActiveSession) {
            try {
                log.debug("Authentication has been done successfully!");
                final ActiveSession session = (ActiveSession) message;
                consumeQueue(createQueue(session));
            } catch (final Exception e) {
                log.error(e, "Queue creation and consumption went wrong!");
                getContext().stop(self());
            }
        } else {
            log.debug("Unknown message {} received!", message);
            getContext().stop(self());
        }
    };

    private Procedure<Object> waitForGameRequestOrAccept = message -> {
        if (message instanceof String) {
            final String str = (String) message;
            if (MessageProtocols.GameRequest.isGameRequestMessage(str)) {
                handleGameRequest(str); //send accepted, wait for game start
            } else if (MessageProtocols.GameRequest.isGameAcceptedMessage(str)) {
                handleGameAccepted(str); //send game start, start gaming
            }
        }
    };

    private Procedure<Object> waitForGameStart = message -> {
        if (message instanceof String) {
            final String str = (String) message;
            if (MessageProtocols.GameRequest.isGameStartMessage(str)) {
                handleGameStart(str); //start gaming
            }
        } else if (message instanceof ReceiveTimeout) {
            waitForGameRequest();
        }
    };

    private Procedure<Object> gaming = message -> {
        if (message instanceof String) {
            final String str = (String) message;
            if (MessageProtocols.GameRequest.isGameRejectedMessage(str)) {
                waitForGameRequest();
            }
        } else if (message instanceof ReceiveTimeout) {
            waitForGameRequest();
        }
    };


    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof String) {
            getContext().become(waitForAuthentication);
            handleCredentials((String) message);
        }
    }


    // ==========================================================================
    // Actor hooks
    // ==========================================================================
    @Override
    public void postStop() throws Exception {
        super.postStop();

        if (playing) {
            log.debug("Socket handler getting close, notifying opponent {} to stop playing", opponentUserId);
            publishMessage(MessageProtocols.GameRequest.buildRejectMessage(session.getUserId()), opponentUserId);
        }

        if (consumingChannel != null && consumingChannel.isOpen()) {
            consumingChannel.close();
        }

        //TODO: makes user offline

        log.debug("Socket Handler has been killed!");
    }

}

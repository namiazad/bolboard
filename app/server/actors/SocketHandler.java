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
import model.Game;
import model.User;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static utils.SafeChannel.managed;
import static model.MessageProtocols.GameProtocol.*;

public class SocketHandler extends UntypedActor {
    private final static FiniteDuration GAME_START_TIMEOUT = new FiniteDuration(15, TimeUnit.SECONDS);
    private final static FiniteDuration GAME_MOVEMENT_TIMEOUT = new FiniteDuration(5, TimeUnit.MINUTES);

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
    private Game game;


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
                            || isGameInstructionMessage(deliveredMessage)
                            || isGameRejectedMessage(deliveredMessage)) {
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

    private void pushToMQ(final String message, final String routingKey) {
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

    private void pushToSocket(final String message) {
        out.tell(message, self());
    }

    private void waitForGameRequest() {
        log.debug("User {} changed the state to waiting for a game request!", session.getUserId());
        playing = false;
        opponentUserId = null;
        game = null;

        pushToSocket(buildSocketWaitingForRequestMessage());

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
        final String requester = fetchRequester(gameRequest);
        final String acceptedMessage = buildAcceptMessage(session.getUserId());

        pushToMQ(acceptedMessage, requester);
        log.debug("Game request from user {} accepted by user {}.", requester, session.getUserId());
        getContext().setReceiveTimeout(GAME_START_TIMEOUT);
        getContext().become(waitForGameStart);
    }

    private void handleGameAccepted(final String accept) {
        final String opponent = fetchAccepter(accept);
        final String gameStartMessage = buildStartMessage(session.getUserId());
        pushToMQ(gameStartMessage, opponent);
        startGame(opponent);
    }

    private void handleGameStart(final String gameStart) {
        final String opponent = fetchStarter(gameStart);
        startGame(opponent);
    }

    private void handleGameInstruction(final String gameInstructionMessage) {
        try {
            final String instruction = fetchGameInstruction(gameInstructionMessage);
            if (instruction != null) {
                final int pitIndex = Integer.parseInt(instruction);
                if (game != null) {
                    if (game.getTurn()) {
                        pushToMQ(gameInstructionMessage, opponentUserId);
                    }

                    final boolean isEnded = game.move(game.normalized(pitIndex));

                    pushToSocket(buildGameStateMessage(game));

                    if (isEnded) {
                        pushToSocket(buildGameEndMessage());
                    } else {
                        notifyTurn();
                    }
                }
            }
        } catch (NumberFormatException nfe) {
            log.warning("Invalid move receive from the client: {}", gameInstructionMessage);
        }
    }

    private void notifyTurn() {
        if (game != null) {
            final String turnMessage = buildGameTurnMessage(game.getTurn());
            log.debug("Turn message {} sent via web socket", turnMessage);
            pushToSocket(turnMessage);
        }
    }

    private Game createGame() {
        log.debug("{} compareTo {} is {}", opponentUserId, session.getUserId(), opponentUserId.compareTo(session.getUserId()) < 0);
        if (opponentUserId.compareTo(session.getUserId()) < 0) {
            final boolean turn = false;
            return new Game(turn, Game.SMALL_PIT_NUMBER_PER_USER + 1);
        } else {
            final boolean turn = true;
            return new Game(turn, 0);
        }
    }

    private void startGame(final String opponentUserId) {
        this.opponentUserId = opponentUserId;
        this.playing = true;
        log.debug("Game between user {} and {} started.", session.getUserId(), this.opponentUserId);

        final User opponent = User.findByUserId(opponentUserId);
        if (opponent != null) {
            pushToSocket(buildSocketGameStartMessage(opponent.getDisplayName()));
            game = createGame();
            notifyTurn();
        } else {
            waitForGameRequest();
        }

        getContext().setReceiveTimeout(GAME_MOVEMENT_TIMEOUT);
        getContext().become(gaming);
    }

    protected void makeUserOffline() {
        //Making user offline. At this moment, the system does not work properly if a user uses multiple browser at a time.
        try {
            if (session != null) {
                final User user = User.findByUserId(session.getUserId());

                if (user != null) {
                    user.copy().online(false).build().update();
                }
            }
        } catch (final Exception ex) {
            log.error(ex, "Making user offline failed due to: ");
        }
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
            if (isGameRequestMessage(str)) {
                handleGameRequest(str); //send accepted, wait for game start
            } else if (isGameAcceptedMessage(str)) {
                handleGameAccepted(str); //send game start, start gaming
            }
        }
    };

    private Procedure<Object> waitForGameStart = message -> {
        if (message instanceof String) {
            final String str = (String) message;
            if (isGameStartMessage(str)) {
                handleGameStart(str); //start gaming
            }
        } else if (message instanceof ReceiveTimeout) {
            waitForGameRequest();
        }
    };

    private Procedure<Object> gaming = message -> {
        log.debug("Message {} received in the game.", message);

        if (message instanceof String) {
            final String str = (String) message;
            if (isGameRejectedMessage(str)) {
                waitForGameRequest();
            } else if (isGameInstructionMessage(str)) {
                handleGameInstruction(str);
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

        makeUserOffline();

        if (playing) {
            log.debug("Socket handler getting close, notifying opponent {} to stop playing", opponentUserId);
            pushToMQ(buildRejectMessage(session.getUserId()), opponentUserId);
        }

        if (consumingChannel != null && consumingChannel.isOpen()) {
            consumingChannel.close();
        }

        log.debug("Socket Handler has been killed!");
    }

}

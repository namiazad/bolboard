package model;

import play.mvc.Http;

import javax.annotation.Nullable;

public class MessageProtocols {

    public static class Exceptions {
        public static class UserCreationException extends RuntimeException {

        }

        public static class InvalidTokenException extends RuntimeException {

        }
    }

    public static class CreateSession {
        private final Principal principal;
        private final Http.Context requestContext;

        public CreateSession(final Principal principal,
                             final Http.Context requestContext) {
            this.principal = principal;
            this.requestContext = requestContext;
        }

        public Principal getPrincipal() {
            return principal;
        }

        public Http.Context getRequestContext() {
            return requestContext;
        }
    }

    public static class Search {
        private final String content;
        private final ActiveSession session;

        public Search(ActiveSession session, String content) {
            this.session = session;
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public ActiveSession getSession() {
            return session;
        }
    }

    public static class GameProtocol {
        public static final String MQ_GAME_REQUEST_PREFIX = "game_request";
        public static final String MQ_GAME_REQUEST_ACCEPTED_PREFIX = "accept";
        public static final String MQ_GAME_REQUEST_REJECTED_PREFIX = "reject";
        public static final String MQ_GAME_START_PREFIX = "start";
        public static final String GAME_INSTRUCTION_PREFIX = "##";
        public static final String GAME_WHOSE_TURN_INSTRUCTION = "turn";
        public static final String GAME_NOT_WHOSE_TURN_INSTRUCTION = "~turn";

        public static final String SOCKET_GAME_START_PREFIX = "opponent";
        public static final String SOCKET_GAME_WAITING = "wait-for-game";

        /**
         * Creates a message to be published to RabbitMQ showing that the user accepts game request
         */
        public static String buildAcceptMessage(final String accepter) {
            return String.format("%s=%s", MQ_GAME_REQUEST_ACCEPTED_PREFIX, accepter);
        }

        /**
         * Creates a message to be published to RabbitMQ showing that the user rejects game request
         */
        public static String buildRejectMessage(final String rejector) {
            return String.format("%s=%s", MQ_GAME_REQUEST_REJECTED_PREFIX, rejector);
        }

        /**
         * Creates a message to be published to RabbitMQ showing that the game started
         */
        public static String buildStartMessage(final String requester) {
            return String.format("%s=%s", MQ_GAME_START_PREFIX, requester);
        }

        /**
         * Creates game controlling message to be published to MQ and Socket.
         */
        public static String buildGameInstructionMessage(final String instruction) {
            return String.format("%s%s", GAME_INSTRUCTION_PREFIX, instruction);
        }

        /**
         * Creates a message to be sent via socket to notify the user that the game started
         */
        public static String buildSocketGameStartMessage(final String opponent) {
            return String.format("%s=%s", SOCKET_GAME_START_PREFIX, opponent);
        }

        /**
         * Creates a message to be sent via socket to notify the user that he/she should wait for another game
         * @return
         */
        public static String buildSocketWaitingForRequestMessage() {
            return SOCKET_GAME_WAITING;
        }

        public static boolean isGameRequestMessage(final String message) {
            return message.startsWith(MQ_GAME_REQUEST_PREFIX);
        }

        public static boolean isGameAcceptedMessage(final String message) {
            return message.startsWith(MQ_GAME_REQUEST_ACCEPTED_PREFIX);
        }

        public static boolean isGameRejectedMessage(final String message) {
            return message.startsWith(MQ_GAME_REQUEST_REJECTED_PREFIX);
        }

        public static boolean isGameStartMessage(final String message) {
            return message.startsWith(MQ_GAME_START_PREFIX);
        }

        public static boolean isGameInstructionMessage(final String message) {
            return message.startsWith(GAME_INSTRUCTION_PREFIX);
        }

        @Nullable
        public static String fetchRequester(final String message) {
            if (isGameRequestMessage(message)) {
                return message.replace(MQ_GAME_REQUEST_PREFIX + "=", "");
            }
            return null;
        }

        @Nullable
        public static String fetchAccepter(final String message) {
            if (isGameAcceptedMessage(message)) {
                return message.replace(MQ_GAME_REQUEST_ACCEPTED_PREFIX + "=", "");
            }
            return null;
        }

        @Nullable
        public static String fetchStarter(final String message) {
            if (isGameStartMessage(message)) {
                return message.replace(MQ_GAME_START_PREFIX + "=", "");
            }
            return null;
        }

        @Nullable
        public static String fetchGameInstruction(final String message) {
            if (isGameInstructionMessage(message)) {
                return message.replace(GAME_INSTRUCTION_PREFIX, "");
            }
            return null;
        }
    }

    public static class GameRequest {

        private final ActiveSession requester;
        private final String target;

        public GameRequest(ActiveSession requester, String target) {
            this.requester = requester;
            this.target = target;
        }

        public ActiveSession getRequester() {
            return requester;
        }

        public String getTarget() {
            return target;
        }

        /**
         * Creates a message to be published to RabbitMQ to request a user to play
         */
        public String buildRequestMessage() {
            return String.format("%s=%s", GameProtocol.MQ_GAME_REQUEST_PREFIX, getRequester().getUserId());
        }


    }
}

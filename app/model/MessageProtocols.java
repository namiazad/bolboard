package model;

import play.mvc.Http;

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

    public static class GameRequest {
        public static final String MQ_GAME_REQUEST_PREFIX = "game_request";

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

        public String buildMQMessage() {
            return String.format("%s=%s", MQ_GAME_REQUEST_PREFIX, getRequester().getUserId());
        }
    }
}

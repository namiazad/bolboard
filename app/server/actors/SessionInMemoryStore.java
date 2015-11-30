package server.actors;

import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Option;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import model.ActiveSession;
import play.Logger;

import java.util.concurrent.TimeUnit;

/**
 * This actor is responsible to keep the online sessions in memory. In a production system, this actor should be
 * replaced with a distributed cache system such as MemCache or Redis.
 */
public class SessionInMemoryStore extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    public static class CacheSession {
        private final ActiveSession session;

        public CacheSession(ActiveSession session) {
            this.session = session;
        }

        public ActiveSession getSession() {
            return session;
        }
    }

    public static class LoadSession {
        private final String userId;

        public LoadSession(final String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }
    }

    public static class UserNotFoundException extends RuntimeException {

    }

    //A cache from userId to sessionId
    private Cache<String, String> sessions;

    @Override
    public void preStart() throws Exception {
        super.preStart();

        sessions = CacheBuilder.<String, String>newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof CacheSession) {
            final CacheSession session = (CacheSession)message;
            log.debug("User {} is being added to Cache!", session.getSession().getUserId());
            sessions.put(session.getSession().getUserId(), session.getSession().getSessionId());
            sender().tell(session.getSession(), self());
        } else if (message instanceof LoadSession) {
            final LoadSession session = (LoadSession)message;
            final String sessionId = sessions.getIfPresent(session.getUserId());
            if (sessionId == null) {
                sender().tell(new Status.Failure(new UserNotFoundException()), self());
            } else {
                log.debug("User {} is loaded from Cache!", session.getUserId());
                sender().tell(new ActiveSession(session.userId, sessionId), self());
            }
        }
    }
}

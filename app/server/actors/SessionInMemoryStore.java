package server.actors;

import akka.actor.UntypedActor;
import akka.japi.Option;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import model.ActiveSession;
import model.User;

import java.util.concurrent.TimeUnit;

/**
 * This actor is responsible to keep the online sessions in memory. In a production system, this actor should be
 * replaced with a distributed cache system such as MemCache or Redis.
 */
public class SessionInMemoryStore extends UntypedActor {
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

        public LoadSession(String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }
    }

    //A cache from userId to sessionId
    private Cache<String, String> sessions;

    protected void makeUserOnline(final String userId) {
        final User user = User.find.byId(userId);

        if (user != null) {
            user.copy().online(true).build().save();
        }
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        sessions = CacheBuilder.<String, String>newBuilder()
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .removalListener(removal -> {
                    if (removal.getKey() != null) {
                        makeUserOnline(removal.getKey().toString());
                    }
                })
                .build();
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof CacheSession) {
            final CacheSession session = (CacheSession)message;
            sessions.put(session.getSession().getUserId(), session.getSession().getSessionId());
            sender().tell(session.getSession(), self());
        } else if (message instanceof LoadSession) {
            final LoadSession session = (LoadSession)message;
            final String sessionId = sessions.getIfPresent(session.getUserId());
            if (sessionId == null) {
                sender().tell(Option.none(), self());
            } else {
                sender().tell(Option.some(new ActiveSession(session.userId, sessionId)), self());
            }
        }
    }
}

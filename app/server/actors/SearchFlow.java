package server.actors;

import akka.actor.ActorRef;
import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import akka.japi.Util;
import model.ActiveSession;
import model.SearchResult;
import model.User;
import play.libs.F;

import java.util.List;
import java.util.stream.Collectors;

import static akka.pattern.Patterns.ask;

public class SearchFlow extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private final int stepTimeout;
    private final ActorRef sessionStore;

    public SearchFlow(final ActorRef sessionStore, final int stepTimeout) {
        this.sessionStore = sessionStore;
        this.stepTimeout = stepTimeout;
    }

    protected void handlerSearch(final Dispatcher.Search search, final ActorRef responder) {
        F.Promise<List<User>> searchPromise = F.Promise.wrap(
                ask(sessionStore, new SessionInMemoryStore.LoadSession(search.getSession().getUserId()), stepTimeout)
                        .mapTo(Util.classTag(ActiveSession.class)))
                .map(activeSession -> {
                    final List<User> users = User.findByDisplayName(search.getContent());

                    return users.stream().filter(user ->
                            !user.getUserId().equals(search.getSession().getUserId())).collect(Collectors.toList());
                });

        searchPromise.onFailure(throwable -> {
            log.debug("Search failed due to:", throwable);
            responder.tell(new Status.Failure(throwable), self());
            getContext().stop(self());
        });

        searchPromise.onRedeem(users -> {
            log.debug("Search succeeded for phrase {} and had {} matches.", search.getContent(), users.size());
            responder.tell(new SearchResult(users), self());
            getContext().stop(self());
        });
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        if (message instanceof Dispatcher.Search) {
            getContext().become(processing);
            handlerSearch((Dispatcher.Search)message, getSender());
        }
    }

    private Procedure<Object> processing = object -> {
        //while the actor processes the first search, it does not respond to any other message.
    };
}

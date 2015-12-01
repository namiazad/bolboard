package server.actors;

import akka.actor.ActorRef;
import akka.actor.Status;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Procedure;
import model.MessageProtocols;
import model.SearchResult;
import model.User;
import play.libs.F;

import java.util.List;
import java.util.stream.Collectors;

public class SearchFlow extends UntypedActor {
    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    protected void handlerSearch(final MessageProtocols.Search search, final ActorRef responder) {
        F.Promise<List<User>> searchPromise = F.Promise.promise(() -> {
            final List<User> users = User.findOnlineUsersByDisplayName(search.getContent());

            List<User> result = null;
            if (users != null) {
                result = users.stream().filter(user ->
                        !user.getUserId().equals(search.getSession().getUserId())).collect(Collectors.toList());
            }

            return result;
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
        if (message instanceof MessageProtocols.Search) {
            getContext().become(processing);
            handlerSearch((MessageProtocols.Search) message, getSender());
        }
    }

    private Procedure<Object> processing = object -> {
        //while the actor processes the first search, it does not respond to any other message.
    };
}

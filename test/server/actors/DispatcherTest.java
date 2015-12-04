package server.actors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import com.rabbitmq.client.Connection;
import model.MessageProtocols;
import model.Principal;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import play.libs.ws.WSClient;
import play.mvc.Results;
import scala.concurrent.duration.FiniteDuration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DispatcherTest {

    static ActorSystem system;

    private final Principal principal = new Principal("facebook", "12", "Nami", "some-token");

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    public static class ActorUnderTest extends Dispatcher {

        final ActorRef createSessionFlow;

        public ActorUnderTest(WSClient client,
                              Connection mqConnection,
                              ActorRef sessionStore,
                              int stepTimeout,
                              int flowTimeout,
                              ActorRef createSessionFlow) {
            super(client, mqConnection, sessionStore, stepTimeout, flowTimeout);
            this.createSessionFlow = createSessionFlow;
        }

        @Override
        protected ActorRef createSessionFlowActor() {
            return createSessionFlow;
        }
    }

    private ActorRef createDispatcher(final ActorSystem system, final ActorRef createSessionFlow) {
        final WSClient client = mock(WSClient.class);
        final Connection connection = mock(Connection.class);

        return system.actorOf(Props.create(ActorUnderTest.class,
                client,
                connection,
                new TestProbe(system).ref(),
                100,
                100,
                createSessionFlow
        ), UUID.randomUUID().toString());
    }

    @Test
    public void testDispatchCreateSession() {
        new JavaTestKit(system) {
            {
                final JavaTestKit probe = new JavaTestKit(system);
                final ActorRef underTest = createDispatcher(system, probe.getRef());

                underTest.tell(new MessageProtocols.CreateSession(principal, null), getRef());

                new Within(new FiniteDuration(5, TimeUnit.SECONDS)) {
                    @Override
                    protected void run() {
                        probe.expectMsgEquals(principal);
                    }
                };
            }
        };
    }

    @Test
    public void testDispatchReturnsUnauthorizedIfTokenIsInvalid() {
        new JavaTestKit(system) {
            {
                final JavaTestKit probe = new JavaTestKit(system);
                final ActorRef underTest = createDispatcher(system, probe.getRef());

                underTest.tell(new MessageProtocols.CreateSession(principal, null), getRef());

                new Within(new FiniteDuration(5, TimeUnit.SECONDS)) {
                    @Override
                    protected void run() {
                        probe.expectMsgEquals(principal);
                        probe.reply(new Status.Failure(new MessageProtocols.Exceptions.InvalidTokenException()));

                        Results.Status status = expectMsgClass(Results.Status.class);
                        assertThat(status.status(), is(401));
                    }
                };
            }
        };
    }

    //TODO: Rest of the functionality can be tested exactly as above
}

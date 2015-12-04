package model;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;
import scala.concurrent.duration.FiniteDuration;
import server.actors.SessionInMemoryStore;
import server.actors.SocketHandler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SocketHandlerTest {
    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    public static class ActorUnderTest extends SocketHandler {
        public ActorUnderTest(final ActorRef out,
                              final ActorRef sessionStore,
                              final Connection connection) {
            super(out, sessionStore, connection);
        }
    }

    @Test
    public void testStopIfSessionIsInvalid() {
        new JavaTestKit(system) {
            {
                final JavaTestKit outProbe = new JavaTestKit(system);
                final JavaTestKit sessionStoreProbe = new JavaTestKit(system);
                final Connection connection = mock(Connection.class);

                final ActorRef underTest = system.actorOf(
                        Props.create(ActorUnderTest.class,
                                outProbe.getRef(),
                                sessionStoreProbe.getRef(),
                                connection));

                //the session message is in invalid format (correct format: userid=sessionid)
                underTest.tell("wrong-session-message", getRef());

                watch(underTest);

                new Within(new FiniteDuration(4, TimeUnit.SECONDS)) {
                    @Override
                    protected void run() {
                        expectTerminated(underTest);
                        sessionStoreProbe.expectNoMsg();
                    }
                };
            }
        };
    }

    @Test
    public void testLoadSessionIfValid() {
        new JavaTestKit(system) {
            {
                final JavaTestKit outProbe = new JavaTestKit(system);
                final JavaTestKit sessionStoreProbe = new JavaTestKit(system);
                final Connection connection = mock(Connection.class);

                final ActorRef underTest = system.actorOf(
                        Props.create(ActorUnderTest.class,
                                outProbe.getRef(),
                                sessionStoreProbe.getRef(),
                                connection));

                underTest.tell("some-user-id=some-session-id", getRef());

                new Within(new FiniteDuration(4, TimeUnit.SECONDS)) {
                    @Override
                    protected void run() {
                        sessionStoreProbe.expectMsgEquals(new SessionInMemoryStore.LoadSession("some-user-id"));
                    }
                };
            }
        };
    }

    @Test
    public void testConsumeQueueIfSessionIsLoaded() throws IOException {
        new JavaTestKit(system) {
            {
                final JavaTestKit outProbe = new JavaTestKit(system);
                final JavaTestKit sessionStoreProbe = new JavaTestKit(system);
                final Connection connection = mock(Connection.class);

                final Channel channel = mock(Channel.class);
                final AMQP.Queue.DeclareOk ok = mock(AMQP.Queue.DeclareOk.class);
                when(connection.createChannel()).thenReturn(channel, channel);
                when(channel.queueDeclare(anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyMap())).thenReturn(ok);
                when(ok.getQueue()).thenReturn("MyQueue");

                final ActorRef underTest = system.actorOf(
                        Props.create(ActorUnderTest.class,
                                outProbe.getRef(),
                                sessionStoreProbe.getRef(),
                                connection));

                underTest.tell("some-user-id=some-session-id", getRef());

                new Within(new FiniteDuration(2, TimeUnit.SECONDS)) {
                    @Override
                    protected void run() {
                        sessionStoreProbe.expectMsgEquals(new SessionInMemoryStore.LoadSession("some-user-id"));
                        sessionStoreProbe.reply(new ActiveSession("some-user-id", "some-session-id"));

                        //If the invocation is not verified, within will timeout after 2 seconds
                        while (true) {
                            try {
                                verify(channel).basicConsume(
                                        Matchers.eq("MyQueue"),
                                        Matchers.eq(false),
                                        Matchers.any(DefaultConsumer.class));

                                return;
                            } catch (IOException e) {
                                fail();
                            } catch (Throwable ex) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    //Do Nothing
                                }
                            }
                        }
                    }
                };
            }
        };
    }

    //TODO: The rest of functionality can be tested exactly as above.
    // As you seen, since the component are loosely coupled, they can be easily mocked.

}

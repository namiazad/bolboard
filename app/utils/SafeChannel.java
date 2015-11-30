package utils;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import play.Logger;

import java.io.IOException;

public class SafeChannel {
    @FunctionalInterface
    public interface CheckedFunction<T, R> {
        R apply(T t) throws IOException;
    }

    public static <T> T managed(final Connection connection,
                                final CheckedFunction<Connection, Channel> channelFactory,
                                final CheckedFunction<Channel, T> f) {
        final boolean keepChannelOpen = false;
        return managed(connection, channelFactory, f, keepChannelOpen);
    }

    public static <T> T managed(final Connection connection,
                                final CheckedFunction<Connection, Channel> channelFactory,
                                final CheckedFunction<Channel, T> f,
                                final boolean keepChannelOpen) {
        Channel channel = null;
        try {
            channel = channelFactory.apply(connection);
            return f.apply(channel);
        } catch (final Exception io) {
            Logger.error("Channel creation failed due to: ", io);
            throw new RuntimeException(io);
        } finally {
            if (!keepChannelOpen && channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (final IOException ioe) {
                    // Do Nothing
                }
            }
        }
    }
}

package utils;

public class ExceptionUtils {
    public static <T extends Throwable> T withCause(final T exception, final Throwable cause) {
        exception.initCause(cause);
        return exception;
    }
}

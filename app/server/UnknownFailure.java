package server;

public class UnknownFailure {
    final Throwable cause;

    public UnknownFailure(final Throwable cause) {
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }
}

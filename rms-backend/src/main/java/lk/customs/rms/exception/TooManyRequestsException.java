package lk.customs.rms.exception;

/**
 * Thrown when a client exceeds the allowed number of failed login attempts within the throttle
 * window. Mapped to HTTP 429 (Too Many Requests) by {@code GlobalExceptionHandler}.
 */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

package com.textrefinery.domain.port;

public class AutostartException extends RuntimeException {

    public AutostartException(String message) {
        super(message);
    }

    public AutostartException(String message, Throwable cause) {
        super(message, cause);
    }
}

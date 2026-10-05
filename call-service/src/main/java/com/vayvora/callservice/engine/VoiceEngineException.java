package com.vayvora.callservice.engine;

import org.springframework.http.HttpStatus;

/** Failure talking to the voice engine, carrying the status to report upstream. */
public class VoiceEngineException extends RuntimeException {
    private final HttpStatus status;

    public VoiceEngineException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public VoiceEngineException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** The engine reports 404 when a session id is unknown or has timed out. */
    public boolean isSessionNotFound() {
        return status == HttpStatus.NOT_FOUND;
    }
}

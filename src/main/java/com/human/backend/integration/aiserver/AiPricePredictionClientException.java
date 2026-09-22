package com.human.backend.integration.aiserver;

public class AiPricePredictionClientException extends RuntimeException {

    public enum FailureType {
        NOT_FOUND,
        TIMEOUT,
        UNAVAILABLE,
        SERVER_ERROR,
        INVALID_RESPONSE
    }

    private final FailureType failureType;

    public AiPricePredictionClientException(
            FailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType getFailureType() {
        return failureType;
    }
}

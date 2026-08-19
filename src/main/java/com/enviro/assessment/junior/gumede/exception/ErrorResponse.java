package com.enviro.assessment.junior.gumede.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

// One shape for every error the API returns, regardless of which handler produced it - a client only ever
// needs to learn this shape once. fieldErrors is the only piece that's genuinely optional: it's populated for
// validation failures (one entry per invalid field) and omitted entirely everywhere else, rather than always
// being present as an empty/null map that callers would have to check for no reason.
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }
}

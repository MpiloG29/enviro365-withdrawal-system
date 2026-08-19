package com.enviro.assessment.junior.gumede.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

// @RestControllerAdvice - a single, centralised place to turn exceptions into HTTP responses, so no controller
// method needs its own try/catch. It's the combination of @ControllerAdvice (applies these @ExceptionHandlers
// to every @RestController in the app) and @ResponseBody (return values are serialized straight to JSON,
// exactly like a normal controller method) - no manual ResponseEntity building required here.
//
// Handler-selection note, since it looks like declaration order might matter and doesn't: Spring picks the
// @ExceptionHandler whose exception type is the closest match in the thrown exception's class hierarchy, not
// the first one declared in the class. WithdrawalRuleException and EntityNotFoundException both extend
// RuntimeException, which extends Exception, but they're still matched to their own specific handlers ahead
// of the catch-all Exception handler below - the "closest match wins" rule is what makes that safe.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // A broken business rule (e.g. withdrawal exceeds 90% of balance) - the request was well-formed, it just
    // isn't allowed given the current state of the account. 422 Unprocessable Entity, not 400: nothing about
    // the request syntax is wrong.
    @ExceptionHandler(WithdrawalRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleWithdrawalRule(WithdrawalRuleException ex, HttpServletRequest request) {
        log.warn("Withdrawal rule violated on {}: {}", request.getRequestURI(), ex.getMessage());
        return ErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
    }

    // Thrown by the service layer (investor/product lookups) when an id doesn't exist - not a client mistake
    // in how the request was built, just a resource that isn't there. 404.
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Entity not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
    }

    // Thrown by Spring when @Valid fails on a @RequestBody DTO (e.g. WithdrawalRequest). Unlike the other
    // handlers, the useful detail here is per-field, so it's the one case that populates fieldErrors -
    // "productId: Product id is required" is far more actionable to a client than one flattened message.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failed on {}: {}", request.getRequestURI(), fieldErrors);
        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields.",
                request.getRequestURI(),
                fieldErrors);
    }

    // Thrown when the request body can't even be parsed into a WithdrawalRequest - invalid JSON, an amount
    // sent as "abc" instead of a number, a completely empty body. ex.getMessage() here is a Jackson error
    // that names the target Java field and type ("Cannot deserialize value of type BigDecimal from String..."),
    // which is exactly the kind of internal detail this handler exists to hold back - it goes to the WARN log,
    // not the client, same principle as the 500 handler below.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMalformedRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Malformed request body.",
                request.getRequestURI());
    }

    // Thrown by InvestorService.getStatementCsv when ?from is after ?to - a nonsensical query parameter
    // combination, not a lookup failure or a business rule. Without this handler it would fall through to the
    // catch-all below and 500, which is wrong: the client sent a bad request, the server didn't fail.
    @ExceptionHandler(InvalidDateRangeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidDateRange(InvalidDateRangeException ex, HttpServletRequest request) {
        log.warn("Invalid date range on {}: {}", request.getRequestURI(), ex.getMessage());
        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());
    }

    // Thrown by Spring MVC itself whenever a request doesn't match any controller mapping or static resource
    // - a typo'd URL, a disabled endpoint (e.g. /h2-console when spring.h2.console.enabled=false), a missing
    // asset. Without this handler it falls through to the catch-all below: NoResourceFoundException is a
    // RuntimeException with no more specific handler registered, so "closest match wins" matches it to
    // Exception.class and reports a routine 404 as a 500, complete with an ERROR-level stack trace for what
    // is not a server bug at all. Caught this by actually curling a disabled endpoint after containerizing
    // the app, not by reading the code - it would have looked identical to a real bug in production logs.
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("No resource found for {}", request.getRequestURI());
        return ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "The requested resource was not found.",
                request.getRequestURI());
    }

    // Catch-all for anything not already handled - a bug, not a client error. The message returned to the
    // client is a fixed, generic string on purpose: ex.getMessage() or ex.getClass().getName() can leak
    // internal detail (SQL fragments, package structure, field names) that's harmless in a stack trace but
    // is exactly the kind of information disclosure a client response shouldn't carry. The real detail goes
    // to the server log at ERROR with the full stack trace instead, which is where whoever's debugging this
    // actually needs it.
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI());
    }
}

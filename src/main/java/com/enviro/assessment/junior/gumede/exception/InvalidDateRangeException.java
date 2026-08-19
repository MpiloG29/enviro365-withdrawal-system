package com.enviro.assessment.junior.gumede.exception;

// Unchecked, same reasoning as WithdrawalRuleException - thrown from the service, caught centrally by
// GlobalExceptionHandler and turned into a 400. Distinct from WithdrawalRuleException on purpose: this is a
// malformed query parameter (from after to), not a broken withdrawal rule, so it can't share that 422 mapping.
public class InvalidDateRangeException extends RuntimeException {

    public InvalidDateRangeException(String message) {
        super(message);
    }
}

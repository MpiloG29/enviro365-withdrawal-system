package com.enviro.assessment.junior.gumede.exception;

// Unchecked (RuntimeException) - a broken business rule shouldn't force every caller up the stack to declare/catch it;
// it's caught once, centrally, by the global exception handler (step 7) and turned into a 422 response.
public class WithdrawalRuleException extends RuntimeException {

    public WithdrawalRuleException(String message) {
        super(message);
    }
}

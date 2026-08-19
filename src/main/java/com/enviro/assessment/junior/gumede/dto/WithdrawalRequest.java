package com.enviro.assessment.junior.gumede.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

// Request body for POST /api/withdrawals.
//
// amount is validated at two layers on purpose, not redundantly:
// - Here (Bean Validation, 400): @Positive and @Digits reject *structurally* invalid input - missing,
//   zero/negative, malformed precision - before a transaction or a repository call ever happens.
// - In WithdrawalService (422 WithdrawalRuleException): the same "must be > 0" check is re-asserted as a
//   business rule, because the service is a public method other callers (a batch job, a CSV importer, a
//   unit test with mocked repositories) can invoke directly, bypassing this DTO and Bean Validation entirely.
//   A public method has to defend its own invariants regardless of who calls it - that's the actual reason
//   to keep both, not just belt-and-braces for its own sake.
// @Digits(integer = 17, fraction = 2) mirrors the @Column(precision = 19, scale = 2) on the money columns:
// 19 total digits, 2 reserved for the fraction, leaves 17 for the integer part.
public record WithdrawalRequest(

        @NotNull(message = "Product id is required")
        Long productId,

        @NotNull(message = "Withdrawal amount is required")
        @Positive(message = "Withdrawal amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Withdrawal amount must have at most 2 decimal places and fit within 19 digits")
        BigDecimal amount
) {
}

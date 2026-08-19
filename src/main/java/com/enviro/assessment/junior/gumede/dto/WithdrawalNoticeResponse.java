package com.enviro.assessment.junior.gumede.dto;

import com.enviro.assessment.junior.gumede.domain.WithdrawalNotice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Response body for a single withdrawal - returned from POST /api/withdrawals and reused as each row of the
// withdrawal history / CSV statement. Carries productId/productName rather than a nested ProductResponse:
// the caller doesn't need the product's *current* balance here (that would be misleading on a statement row -
// it's not the balance this notice recorded), just enough to identify the product by name.
//
// from(notice) reads notice.getProduct().getName() - safe only because Product is a LAZY association on
// WithdrawalNotice. Whatever loads the WithdrawalNotice in the first place (WithdrawalService, and later the
// statements endpoint) must do so, and call this mapper, inside an open transaction/session - otherwise
// touching getProduct() throws LazyInitializationException once that session has closed.
public record WithdrawalNoticeResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDateTime requestedAt
) {

    public static WithdrawalNoticeResponse from(WithdrawalNotice notice) {
        return new WithdrawalNoticeResponse(
                notice.getId(),
                notice.getProduct().getId(),
                notice.getProduct().getName(),
                notice.getAmount(),
                notice.getBalanceAfter(),
                notice.getRequestedAt()
        );
    }
}

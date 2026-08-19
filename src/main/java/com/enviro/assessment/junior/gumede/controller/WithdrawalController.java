package com.enviro.assessment.junior.gumede.controller;

import com.enviro.assessment.junior.gumede.dto.WithdrawalNoticeResponse;
import com.enviro.assessment.junior.gumede.dto.WithdrawalRequest;
import com.enviro.assessment.junior.gumede.service.WithdrawalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// One endpoint, one job: bind the body, hand it to WithdrawalService, return whatever comes back. Which of
// the three failure statuses (400/404/422) applies is never decided here - @Valid triggers Bean Validation
// before this method body even runs (400 on failure, via GlobalExceptionHandler), and the two exceptions the
// service can throw (EntityNotFoundException, WithdrawalRuleException) propagate straight past this method
// to the same handler. There is nothing to catch here on purpose.
@RestController
@RequestMapping("/api/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    // @Valid - triggers Bean Validation against WithdrawalRequest's @NotNull/@Positive/@Digits constraints
    // before this method runs at all; a failure never reaches the method body.
    // 201 Created, not the default 200: a withdrawal notice is a new resource being created, and the response
    // body is that resource's representation - exactly the case 201 exists for.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WithdrawalNoticeResponse submitWithdrawal(@Valid @RequestBody WithdrawalRequest request) {
        return withdrawalService.submitWithdrawal(request.productId(), request.amount());
    }
}

package com.enviro.assessment.junior.gumede.service;

import com.enviro.assessment.junior.gumede.domain.Investor;
import com.enviro.assessment.junior.gumede.domain.Product;
import com.enviro.assessment.junior.gumede.domain.ProductType;
import com.enviro.assessment.junior.gumede.domain.WithdrawalNotice;
import com.enviro.assessment.junior.gumede.dto.WithdrawalNoticeResponse;
import com.enviro.assessment.junior.gumede.exception.WithdrawalRuleException;
import com.enviro.assessment.junior.gumede.repository.ProductRepository;
import com.enviro.assessment.junior.gumede.repository.WithdrawalNoticeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

// @Service - marks this as a Spring-managed bean holding business logic, distinct from @Repository (data access)
// and @RestController (HTTP concerns); keeps the rules in one place instead of scattered across controllers.
@Service
public class WithdrawalService {

    // The fraction of a product's balance an investor may withdraw in a single notice.
    private static final BigDecimal MAX_WITHDRAWAL_FRACTION = new BigDecimal("0.90");

    private static final int MINIMUM_RETIREMENT_AGE = 65;

    private final ProductRepository productRepository;
    private final WithdrawalNoticeRepository withdrawalNoticeRepository;

    // Constructor injection - dependencies are explicit and final, so the service can never be constructed in a
    // half-wired state, and it's trivial to pass mocks in a unit test without needing Spring at all.
    public WithdrawalService(ProductRepository productRepository,
                              WithdrawalNoticeRepository withdrawalNoticeRepository) {
        this.productRepository = productRepository;
        this.withdrawalNoticeRepository = withdrawalNoticeRepository;
    }

    // @Transactional - the balance update and the notice insert must both succeed or both roll back; without this,
    // a failure after debiting the balance but before saving the notice would silently lose money from the statement.
    //
    // Returns the DTO, not the WithdrawalNotice entity, and maps it here rather than leaving that to the
    // controller: notice.getProduct() happens to be safe to touch after this method returns (product was
    // loaded as a real entity above, not a lazy proxy), but mapping inside the transaction is the pattern
    // that stays correct even where that coincidence doesn't hold - e.g. the statements endpoint, which loads
    // notices where Product genuinely is a lazy, unloaded proxy until something inside an open session touches it.
    @Transactional
    public WithdrawalNoticeResponse submitWithdrawal(Long productId, BigDecimal amount) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id " + productId));

        validateAmountIsPositive(amount);
        validateRetirementAge(product);
        validateDoesNotExceedBalance(amount, product.getBalance());
        validateDoesNotExceedNinetyPercentCap(amount, product.getBalance());

        BigDecimal balanceAfter = product.getBalance().subtract(amount);
        product.setBalance(balanceAfter);
        productRepository.save(product);

        WithdrawalNotice notice = new WithdrawalNotice(product, amount, balanceAfter);
        WithdrawalNotice saved = withdrawalNoticeRepository.save(notice);
        return WithdrawalNoticeResponse.from(saved);
    }

    // Rejects zero and negative amounts up front - a "withdrawal" of nothing (or less than nothing) isn't a
    // business-rule violation about the account, it's an invalid request, so it's checked before anything
    // account-specific.
    private void validateAmountIsPositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new WithdrawalRuleException("Withdrawal amount must be greater than zero.");
        }
    }

    // Retirement products can only be drawn down once the investor is strictly older than 65; savings products
    // have no age restriction at all. Age comes from Investor.getAge() rather than being computed here - the
    // same method backs the age shown on the portfolio dashboard, so there's exactly one place in the codebase
    // that knows how to turn a date of birth into an age.
    private void validateRetirementAge(Product product) {
        if (product.getType() != ProductType.RETIREMENT) {
            return;
        }
        Investor investor = product.getInvestor();
        int age = investor.getAge();
        if (age <= MINIMUM_RETIREMENT_AGE) {
            throw new WithdrawalRuleException(
                    "Retirement withdrawals are only permitted for investors older than "
                            + MINIMUM_RETIREMENT_AGE + ". Investor is " + age + " years old.");
        }
    }

    // Checked before the 90% cap deliberately: exceeding the full balance is always also true when exceeding
    // 90% of it, so if we checked the cap first, an investor asking to withdraw more than they even have would
    // be told "you're over the 90% limit" - technically true but not the real, more fundamental problem. This
    // ordering means each error message is the one that actually explains what's wrong.
    private void validateDoesNotExceedBalance(BigDecimal amount, BigDecimal balance) {
        if (amount.compareTo(balance) > 0) {
            throw new WithdrawalRuleException(
                    "Withdrawal amount of " + amount + " exceeds the available balance of " + balance + ".");
        }
    }

    private void validateDoesNotExceedNinetyPercentCap(BigDecimal amount, BigDecimal balance) {
        // RoundingMode.DOWN - always rounds the cap towards zero (never up), so the investor can never be
        // allowed to withdraw a cent more than the true 90% limit due to rounding in their favour.
        BigDecimal cap = balance.multiply(MAX_WITHDRAWAL_FRACTION).setScale(2, RoundingMode.DOWN);
        if (amount.compareTo(cap) > 0) {
            throw new WithdrawalRuleException(
                    "Withdrawal amount of " + amount + " exceeds the maximum allowed withdrawal of "
                            + cap + " (90% of balance).");
        }
    }
}

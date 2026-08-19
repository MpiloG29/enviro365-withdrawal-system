package com.enviro.assessment.junior.gumede.service;

import com.enviro.assessment.junior.gumede.domain.Investor;
import com.enviro.assessment.junior.gumede.domain.Product;
import com.enviro.assessment.junior.gumede.domain.ProductType;
import com.enviro.assessment.junior.gumede.domain.WithdrawalNotice;
import com.enviro.assessment.junior.gumede.dto.WithdrawalNoticeResponse;
import com.enviro.assessment.junior.gumede.exception.WithdrawalRuleException;
import com.enviro.assessment.junior.gumede.repository.ProductRepository;
import com.enviro.assessment.junior.gumede.repository.WithdrawalNoticeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// @ExtendWith(MockitoExtension.class) - wires up @Mock/@InjectMocks without needing a Spring context. This is
// specifically the point of constructor injection in WithdrawalService: no @SpringBootTest, no real
// ApplicationContext, no H2 database - just the four rules exercised directly against mocked repositories, in
// milliseconds. If the service depended on field-injected beans instead, this test wouldn't be possible in
// this form at all.
@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WithdrawalNoticeRepository withdrawalNoticeRepository;

    private WithdrawalService withdrawalService;

    private static final Long PRODUCT_ID = 1L;

    // Not @InjectMocks - constructing it by hand in each test keeps the constructor call visible right where
    // it's used, and avoids relying on Mockito's field-name/type matching to guess which mock goes where.
    private WithdrawalService newService() {
        return new WithdrawalService(productRepository, withdrawalNoticeRepository);
    }

    private Product savingsProduct(BigDecimal balance) {
        Investor investor = new Investor("Lerato", "Dube", "lerato.dube@example.com", LocalDate.now().minusYears(35));
        Product product = new Product("Flexible Savings Account", ProductType.SAVINGS, balance, investor);
        product.setId(PRODUCT_ID);
        return product;
    }

    private Product retirementProduct(BigDecimal balance, int investorAge) {
        Investor investor = new Investor("Thandiwe", "Nkosi", "thandiwe.nkosi@example.com", LocalDate.now().minusYears(investorAge));
        Product product = new Product("Retirement Annuity", ProductType.RETIREMENT, balance, investor);
        product.setId(PRODUCT_ID);
        return product;
    }

    // ---- Happy path ----

    @Test
    @DisplayName("valid withdrawal debits the balance and saves a notice recording the balance at that moment")
    void submitWithdrawal_debitsBalanceAndSavesNotice_onValidRequest() {
        Product product = savingsProduct(new BigDecimal("1000.00"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        // Simulates what a real save() does: assigns a generated id and hands back the same (now-persisted)
        // entity. Without this, saved.getId() below would just be null.
        when(withdrawalNoticeRepository.save(any(WithdrawalNotice.class))).thenAnswer(invocation -> {
            WithdrawalNotice notice = invocation.getArgument(0);
            notice.setId(99L);
            return notice;
        });

        WithdrawalNoticeResponse response = newService().submitWithdrawal(PRODUCT_ID, new BigDecimal("300.00"));

        // The balance was actually debited on the entity that gets persisted, not just reflected in the
        // response - product is the same object the mocked repository handed back, so this proves the
        // mutation happened before save(), not that the response DTO merely computed the "right" number
        // independently.
        assertThat(product.getBalance()).isEqualByComparingTo("700.00");
        verify(productRepository).save(product);

        ArgumentCaptor<WithdrawalNotice> noticeCaptor = ArgumentCaptor.forClass(WithdrawalNotice.class);
        verify(withdrawalNoticeRepository).save(noticeCaptor.capture());
        WithdrawalNotice savedNotice = noticeCaptor.getValue();
        assertThat(savedNotice.getAmount()).isEqualByComparingTo("300.00");
        assertThat(savedNotice.getBalanceAfter()).isEqualByComparingTo("700.00");
        assertThat(savedNotice.getProduct()).isSameAs(product);

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.productName()).isEqualTo("Flexible Savings Account");
        assertThat(response.amount()).isEqualByComparingTo("300.00");
        assertThat(response.balanceAfter()).isEqualByComparingTo("700.00");
    }

    // ---- Rule 1: amount must be positive ----

    @Test
    @DisplayName("zero or negative amounts are rejected before any account-specific rule is checked")
    void submitWithdrawal_rejectsNonPositiveAmount() {
        Product product = savingsProduct(new BigDecimal("1000.00"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> newService().submitWithdrawal(PRODUCT_ID, new BigDecimal("-50.00")))
                .isInstanceOf(WithdrawalRuleException.class)
                .hasMessageContaining("greater than zero");

        verify(withdrawalNoticeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    // ---- Rule 2: retirement withdrawals require age > 65 ----

    @Test
    @DisplayName("a retirement withdrawal is rejected for an investor who is exactly 65, not strictly older")
    void submitWithdrawal_rejectsRetirementWithdrawal_whenInvestorNotOlderThan65() {
        // Exactly 65 today, not "older than 65" - the boundary the rule is actually about. minusYears(65)
        // rather than a hardcoded date of birth so this test stays correct regardless of when it's run.
        Product product = retirementProduct(new BigDecimal("10000.00"), 65);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> newService().submitWithdrawal(PRODUCT_ID, new BigDecimal("100.00")))
                .isInstanceOf(WithdrawalRuleException.class)
                .hasMessageContaining("older than 65")
                .hasMessageContaining("65 years old");

        verify(withdrawalNoticeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    // ---- Rule 3: amount must not exceed the balance ----

    @Test
    @DisplayName("an amount greater than the balance is rejected, reporting the balance, not the 90% cap")
    void submitWithdrawal_rejectsAmountExceedingBalance() {
        Product product = savingsProduct(new BigDecimal("1000.00"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        // 1500 exceeds both the full balance and the 90% cap - asserting on "available balance" specifically
        // is what proves the balance check fired first, per the deliberate ordering in the service.
        assertThatThrownBy(() -> newService().submitWithdrawal(PRODUCT_ID, new BigDecimal("1500.00")))
                .isInstanceOf(WithdrawalRuleException.class)
                .hasMessageContaining("exceeds the available balance");

        verify(withdrawalNoticeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    // ---- Rule 4: amount must not exceed 90% of the balance ----

    @Test
    @DisplayName("an amount within the balance but over 90% of it is rejected, reporting the 90% cap")
    void submitWithdrawal_rejectsAmountExceedingNinetyPercentCap() {
        Product product = savingsProduct(new BigDecimal("1000.00"));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));

        // 950 <= balance (1000), so rule 3 passes; 950 > 900 (90% of 1000), so this is the rule that must fire.
        assertThatThrownBy(() -> newService().submitWithdrawal(PRODUCT_ID, new BigDecimal("950.00")))
                .isInstanceOf(WithdrawalRuleException.class)
                .hasMessageContaining("exceeds the maximum allowed withdrawal")
                .hasMessageContaining("900.00");

        verify(withdrawalNoticeRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }
}

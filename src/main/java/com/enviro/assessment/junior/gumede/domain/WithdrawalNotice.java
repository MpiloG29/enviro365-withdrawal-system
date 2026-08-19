package com.enviro.assessment.junior.gumede.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// @Entity - maps this class to a database table managed by JPA
@Entity
@Table(name = "withdrawal_notices")
public class WithdrawalNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne - many withdrawal notices can be raised against one product; owning side holds the foreign key
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // BigDecimal - avoids floating point rounding errors when validating against the balance rules
    // @Column(precision, scale) - caps the DB column at 19 total digits with 2 after the decimal point
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    // The product's balance immediately after this withdrawal was applied, captured at creation time by the
    // service layer. Statements must replay history as it happened, not recompute it from today's live balance
    // (which would be wrong for any notice that isn't the most recent one).
    @Column(precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    // Set automatically on save rather than supplied by the client; not user-editable afterwards
    private LocalDateTime requestedAt;

    public WithdrawalNotice() {
    }

    public WithdrawalNotice(Product product, BigDecimal amount, BigDecimal balanceAfter) {
        this.product = product;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    // @PrePersist - JPA lifecycle callback that runs just before the entity is first saved, stamping the request time server-side
    @PrePersist
    void onCreate() {
        this.requestedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }
}

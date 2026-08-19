package com.enviro.assessment.junior.gumede.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// @Entity - maps this class to a database table managed by JPA
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Human-readable label (e.g. "Retirement Annuity", "Flexible Savings Account") - the dashboard and
    // statements show this, not the raw enum, since "RETIREMENT" alone doesn't tell an investor which of
    // their possibly-several retirement products they're looking at.
    private String name;

    // @Enumerated(STRING) - persists the enum name (e.g. "RETIREMENT") as text rather than its ordinal, so column values stay readable and safe if the enum order changes
    @Enumerated(EnumType.STRING)
    private ProductType type;

    // BigDecimal - avoids floating point rounding errors when comparing/calculating money
    // @Column(precision, scale) - caps the DB column at 19 total digits with 2 after the decimal point, matching standard money-column sizing instead of Hibernate's default (19,2 is implicit for Double but not guaranteed for BigDecimal without stating it)
    @Column(precision = 19, scale = 2)
    private BigDecimal balance;

    // @ManyToOne - many products belong to one investor; this is the owning side of the relationship
    // @JoinColumn - names the foreign key column on the "products" table
    // FetchType.LAZY - don't load the parent investor from the DB until it's actually accessed, to avoid unnecessary joins
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    // @OneToMany - one product can have many withdrawal notices raised against it
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WithdrawalNotice> withdrawalNotices = new ArrayList<>();

    public Product() {
    }

    public Product(String name, ProductType type, BigDecimal balance, Investor investor) {
        this.name = name;
        this.type = type;
        this.balance = balance;
        this.investor = investor;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProductType getType() {
        return type;
    }

    public void setType(ProductType type) {
        this.type = type;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Investor getInvestor() {
        return investor;
    }

    public void setInvestor(Investor investor) {
        this.investor = investor;
    }

    public List<WithdrawalNotice> getWithdrawalNotices() {
        return withdrawalNotices;
    }

    public void setWithdrawalNotices(List<WithdrawalNotice> withdrawalNotices) {
        this.withdrawalNotices = withdrawalNotices;
    }
}

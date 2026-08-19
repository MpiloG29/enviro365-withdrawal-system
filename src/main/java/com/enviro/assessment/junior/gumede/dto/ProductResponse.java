package com.enviro.assessment.junior.gumede.dto;

import com.enviro.assessment.junior.gumede.domain.Product;
import com.enviro.assessment.junior.gumede.domain.ProductType;

import java.math.BigDecimal;

// Java record - a DTO is just an immutable bundle of data with no behaviour, so a record gives us the
// constructor, accessors, equals/hashCode and toString for free instead of hand-writing boilerplate.
// Deliberately excludes the back-reference to Investor and the list of WithdrawalNotices that the Product
// entity carries - a client asking for a portfolio doesn't need either, and including them is exactly how
// the Investor <-> Product <-> WithdrawalNotice cycle ends up being serialized straight into infinite recursion.
public record ProductResponse(
        Long id,
        String name,
        ProductType type,
        BigDecimal balance
) {

    // Static factory method - keeps the entity-to-DTO mapping right next to the DTO it produces, so there's
    // one obvious place to look for "how does a Product become a ProductResponse" without a separate
    // mapper class/framework (e.g. MapStruct) that would be overkill for four simple fields.
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getType(), product.getBalance());
    }
}

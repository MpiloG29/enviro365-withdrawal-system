package com.enviro.assessment.junior.gumede.domain;

// Plain enum - no JPA annotation needed here; it's persisted via @Enumerated on the owning field in Product
public enum ProductType {
    SAVINGS,
    RETIREMENT
}

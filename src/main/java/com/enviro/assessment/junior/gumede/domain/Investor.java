package com.enviro.assessment.junior.gumede.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

// @Entity - tells JPA this class maps to a database table and should be managed by the persistence context
@Entity
// @Table - explicitly names the table; avoids clashing with the reserved word "USER" some DBs use for a default-named entity
@Table(name = "investors")
public class Investor {

    // @Id - marks this field as the table's primary key
    @Id
    // @GeneratedValue - lets the database auto-increment the id instead of us assigning it manually
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    private String email;

    // Stored as DATE; used to derive age for the retirement withdrawal rule (age > 65) in the service layer
    private LocalDate dateOfBirth;

    // @OneToMany - one investor owns many products; mappedBy means Product.investor is the owning side that holds the foreign key
    // cascade ALL - saving/deleting an investor saves/deletes their products too, since products can't exist without an owner
    // orphanRemoval - if a product is removed from this list, delete it from the database rather than just unlinking it
    @OneToMany(mappedBy = "investor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();

    public Investor() {
    }

    public Investor(String firstName, String lastName, String email, LocalDate dateOfBirth) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    // Derived, not persisted - computed fresh from dateOfBirth on every call, so it's never a day stale the
    // way a stored "age" column would be. This is the one place age is computed at all: the retirement
    // withdrawal rule (WithdrawalService) and the portfolio dashboard (InvestorPortfolioResponse) both call
    // this instead of each running their own Period.between - which is exactly what a JS reimplementation in
    // the frontend used to do too, as a third copy, before it was deleted in favour of this method's result
    // being sent to the client directly.
    public int getAge() {
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public List<Product> getProducts() {
        return products;
    }

    public void setProducts(List<Product> products) {
        this.products = products;
    }
}

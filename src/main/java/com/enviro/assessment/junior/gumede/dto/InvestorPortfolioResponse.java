package com.enviro.assessment.junior.gumede.dto;

import com.enviro.assessment.junior.gumede.domain.Investor;

import java.time.LocalDate;
import java.util.List;

// Response body for GET /api/investors/{id}/portfolio - investor details plus their products, flattened into
// a shape a UI can render directly. Products are mapped to ProductResponse rather than nesting the Product
// entities themselves, so this never carries a Product -> Investor back-reference into the JSON.
//
// age is computed here via Investor.getAge() and sent as a plain number rather than leaving the client to
// derive it from dateOfBirth. Age-from-birthdate is exactly the kind of calculation that looks trivial but
// isn't safe to reimplement twice: a JS version doing its own date arithmetic can disagree with the server's
// java.time.Period.between() around a birthday or a timezone boundary, and that server figure is also what
// the retirement-withdrawal rule (WithdrawalService) checks against 65 - so a client-side mismatch wouldn't
// just be a cosmetic bug, it could show an investor as eligible for a withdrawal the server then rejects.
// dateOfBirth is still included for completeness/display, but nothing should compute age from it again.
public record InvestorPortfolioResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        int age,
        List<ProductResponse> products
) {

    public static InvestorPortfolioResponse from(Investor investor) {
        List<ProductResponse> products = investor.getProducts().stream()
                .map(ProductResponse::from)
                .toList();

        return new InvestorPortfolioResponse(
                investor.getId(),
                investor.getFirstName(),
                investor.getLastName(),
                investor.getEmail(),
                investor.getDateOfBirth(),
                investor.getAge(),
                products
        );
    }
}

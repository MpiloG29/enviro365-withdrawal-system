package com.enviro.assessment.junior.gumede.controller;

import com.enviro.assessment.junior.gumede.dto.InvestorPortfolioResponse;
import com.enviro.assessment.junior.gumede.dto.WithdrawalNoticeResponse;
import com.enviro.assessment.junior.gumede.service.InvestorService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// @RestController - every method's return value is written straight to the HTTP response body as JSON
// (or, for the CSV endpoint, plain text) instead of being resolved to a view name.
//
// Every method here just binds the request, delegates to InvestorService, and returns whatever the service
// gave back - there's no entity, no mapping, no business rule in this class. Both "the investor doesn't
// exist" (404) and "the request is well-formed" (200) are decided by whether InvestorService throws, which
// GlobalExceptionHandler translates - not by anything checked here.
@RestController
@RequestMapping("/api/investors")
public class InvestorController {

    private final InvestorService investorService;

    public InvestorController(InvestorService investorService) {
        this.investorService = investorService;
    }

    @GetMapping("/{id}/portfolio")
    public InvestorPortfolioResponse getPortfolio(@PathVariable Long id) {
        return investorService.getPortfolio(id);
    }

    // "Newest first" is the service's ordering (findByProductInvestorIdOrderByRequestedAtDesc), not something
    // re-sorted here - a controller re-sorting a list the repository already sorted would just be duplicated
    // logic waiting to drift out of sync.
    @GetMapping("/{id}/withdrawals")
    public List<WithdrawalNoticeResponse> getWithdrawalHistory(@PathVariable Long id) {
        return investorService.getWithdrawalHistory(id);
    }

    // from/to are plain calendar dates (?from=2026-01-01&to=2026-06-30), not timestamps - @DateTimeFormat(iso =
    // DATE) is what makes Spring parse "2026-01-01" instead of expecting a full ISO-8601 datetime. Both are
    // optional; the service treats an absent bound as "no limit" on that side.
    //
    // Returned as ResponseEntity<String> rather than a plain String return value because the point of this
    // endpoint is the headers, not just the body: Content-Type: text/csv tells the browser what it's getting,
    // and Content-Disposition: attachment; filename=... is what turns a browser navigation into a download
    // dialog instead of the CSV text rendering inline in the tab.
    @GetMapping("/{id}/statement.csv")
    public ResponseEntity<String> getStatementCsv(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        String csv = investorService.getStatementCsv(id, from, to);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement-" + id + ".csv\"")
                .body(csv);
    }
}

package com.enviro.assessment.junior.gumede.service;

import com.enviro.assessment.junior.gumede.domain.Investor;
import com.enviro.assessment.junior.gumede.domain.WithdrawalNotice;
import com.enviro.assessment.junior.gumede.dto.InvestorPortfolioResponse;
import com.enviro.assessment.junior.gumede.dto.WithdrawalNoticeResponse;
import com.enviro.assessment.junior.gumede.exception.InvalidDateRangeException;
import com.enviro.assessment.junior.gumede.repository.InvestorRepository;
import com.enviro.assessment.junior.gumede.repository.WithdrawalNoticeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Everything here is a read: the investor's portfolio, their withdrawal history, their statement. Submitting
// a withdrawal - the one operation that writes - lives in WithdrawalService instead. Splitting on
// read-vs-write rather than bundling "everything about an investor" into one service keeps this class free
// of the four business rules entirely; nothing here needs to know what makes a withdrawal valid.
@Service
public class InvestorService {

    private final InvestorRepository investorRepository;
    private final WithdrawalNoticeRepository withdrawalNoticeRepository;

    public InvestorService(InvestorRepository investorRepository,
                            WithdrawalNoticeRepository withdrawalNoticeRepository) {
        this.investorRepository = investorRepository;
        this.withdrawalNoticeRepository = withdrawalNoticeRepository;
    }

    // @Transactional(readOnly = true) - keeps the Hibernate session open for the duration of this method, which
    // matters here because Investor.products is a LAZY collection. Mapping to InvestorPortfolioResponse *inside*
    // this method, while the session is still open, means investor.getProducts() can actually be fetched.
    // If a controller instead called findById() and mapped the result itself, the session would already be
    // closed by the time it touched getProducts(), and that throws LazyInitializationException - a very common
    // Spring Boot runtime failure that looks like a broken DTO but is really a transaction-boundary bug.
    // readOnly = true additionally lets Hibernate skip dirty-checking, since a portfolio lookup never writes.
    @Transactional(readOnly = true)
    public InvestorPortfolioResponse getPortfolio(Long investorId) {
        Investor investor = investorRepository.findById(investorId)
                .orElseThrow(() -> new EntityNotFoundException("Investor not found with id " + investorId));
        return InvestorPortfolioResponse.from(investor);
    }

    // Same lazy-loading reasoning as getPortfolio applies to WithdrawalNotice.product here: it's a LAZY
    // @ManyToOne, and WithdrawalNoticeResponse.from() calls getProduct().getName(). Mapping to DTOs while
    // still inside this transaction is what makes that safe.
    @Transactional(readOnly = true)
    public List<WithdrawalNoticeResponse> getWithdrawalHistory(Long investorId) {
        requireInvestorExists(investorId);
        return withdrawalNoticeRepository.findByProductInvestorIdOrderByRequestedAtDesc(investorId).stream()
                .map(WithdrawalNoticeResponse::from)
                .toList();
    }

    // Builds the CSV body as a String and hands it back to the controller to attach headers to and return -
    // the controller doesn't touch a WithdrawalNotice or format a single field. from/to are whole calendar
    // days (LocalDate, no time component): from is inclusive at 00:00, to is inclusive through 23:59:59.999,
    // implemented as "< the day after to" so a withdrawal recorded at any time on the "to" date is included.
    // Either or both may be null, meaning "no lower/upper bound".
    @Transactional(readOnly = true)
    public String getStatementCsv(Long investorId, LocalDate from, LocalDate to) {
        // Checked before the investor lookup, same reasoning as amount-positive-before-age-rule in
        // WithdrawalService: this is a malformed request (the range itself is nonsensical), not something
        // that depends on which investor it is, so it's rejected before spending a query on existence.
        // Without this check, from > to silently matches nothing - both stream filters below would always be
        // false at once - and the endpoint would 200 with a header-only CSV instead of telling the caller
        // their query parameters don't make sense.
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(
                    "'from' (" + from + ") must not be after 'to' (" + to + ").");
        }

        requireInvestorExists(investorId);

        LocalDateTime fromInclusive = from != null ? from.atStartOfDay() : null;
        LocalDateTime toExclusive = to != null ? to.plusDays(1).atStartOfDay() : null;

        List<WithdrawalNotice> notices = withdrawalNoticeRepository
                .findByProductInvestorIdOrderByRequestedAtDesc(investorId).stream()
                .filter(notice -> fromInclusive == null || !notice.getRequestedAt().isBefore(fromInclusive))
                .filter(notice -> toExclusive == null || notice.getRequestedAt().isBefore(toExclusive))
                .toList();

        StringBuilder csv = new StringBuilder();
        csv.append("Withdrawal ID,Product ID,Product Name,Amount,Balance After,Requested At\r\n");
        for (WithdrawalNotice notice : notices) {
            csv.append(notice.getId()).append(',')
                    .append(notice.getProduct().getId()).append(',')
                    .append(escapeCsvField(notice.getProduct().getName())).append(',')
                    .append(notice.getAmount()).append(',')
                    .append(notice.getBalanceAfter()).append(',')
                    .append(notice.getRequestedAt())
                    .append("\r\n");
        }
        return csv.toString();
    }

    private void requireInvestorExists(Long investorId) {
        if (!investorRepository.existsById(investorId)) {
            throw new EntityNotFoundException("Investor not found with id " + investorId);
        }
    }

    // RFC 4180 escaping - only product name is free text (everything else here is a number or a fixed-format
    // timestamp, neither of which can contain a comma or a quote), so this is the one field that needs it.
    // A field is only wrapped in quotes if it actually needs to be; a literal quote inside it is escaped by
    // doubling it, per the spec, rather than backslash-escaping it the way many other formats would.
    private static String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuoting) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

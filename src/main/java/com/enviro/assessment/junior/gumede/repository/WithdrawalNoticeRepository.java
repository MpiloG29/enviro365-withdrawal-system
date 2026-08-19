package com.enviro.assessment.junior.gumede.repository;

import com.enviro.assessment.junior.gumede.domain.WithdrawalNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WithdrawalNoticeRepository extends JpaRepository<WithdrawalNotice, Long> {

    // Derived query - all withdrawal notices across every product belonging to a given investor, joining
    // through Product.investor, newest first. Backs both the withdrawal history endpoint and the CSV
    // statement, which both need the same investor-scoped, newest-first ordering.
    List<WithdrawalNotice> findByProductInvestorIdOrderByRequestedAtDesc(Long investorId);
}

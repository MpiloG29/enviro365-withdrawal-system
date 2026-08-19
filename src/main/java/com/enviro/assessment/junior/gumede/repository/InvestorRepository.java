package com.enviro.assessment.junior.gumede.repository;

import com.enviro.assessment.junior.gumede.domain.Investor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// @Repository - marks this as a Spring-managed data access bean and enables JPA's exception translation to Spring's DataAccessException hierarchy
@Repository
// JpaRepository<Investor, Long> - gives us CRUD + pagination/sorting for Investor out of the box, keyed by its Long id, with no implementation code needed
public interface InvestorRepository extends JpaRepository<Investor, Long> {
}

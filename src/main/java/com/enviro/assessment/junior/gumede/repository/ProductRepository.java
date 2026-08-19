package com.enviro.assessment.junior.gumede.repository;

import com.enviro.assessment.junior.gumede.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Derived query - Spring Data parses the method name and generates "select p from Product p where p.investor.id = ?1"
    List<Product> findByInvestorId(Long investorId);
}

package com.example.demo.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.domain.MonthlyReport;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {
    Optional<MonthlyReport> findByReportingYearAndReportingMonth(int year, int month);

    @EntityGraph(attributePaths = "branches")
    Optional<MonthlyReport> findWithBranchesById(Long id);

    @EntityGraph(attributePaths = "branches")
    Optional<MonthlyReport> findWithBranchesByReportingYearAndReportingMonth(int year, int month);

    @EntityGraph(attributePaths = "branches")
    List<MonthlyReport> findAllByOrderByReportingYearDescReportingMonthDesc();
}

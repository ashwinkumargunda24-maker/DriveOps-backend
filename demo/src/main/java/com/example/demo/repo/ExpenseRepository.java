package com.example.demo.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.Expense;
import com.example.demo.domain.Codes.ExpenseCategory;
import com.example.demo.domain.Codes.ExpenseStatus;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @Query("""
        SELECT e FROM Expense e WHERE
        (:branchId IS NULL OR e.branch.id = :branchId) AND
        (:organizationId IS NULL OR e.branch.organization.id = :organizationId) AND
        (:status IS NULL OR e.status = :status) AND
        (:vehicleId IS NULL OR e.vehicle.id = :vehicleId) AND
        (:q IS NULL OR LOWER(COALESCE(e.description, '')) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<Expense> search(@Param("branchId") Long branchId, @Param("organizationId") Long organizationId,
                         @Param("status") ExpenseStatus status, @Param("vehicleId") Long vehicleId,
                         @Param("q") String q, Pageable pageable);

    @Query("""
        SELECT e FROM Expense e WHERE
        e.driver.id = :driverId AND
        (:branchId IS NULL OR e.branch.id = :branchId) AND
        (:organizationId IS NULL OR e.branch.organization.id = :organizationId) AND
        (:status IS NULL OR e.status = :status) AND
        (:vehicleId IS NULL OR e.vehicle.id = :vehicleId) AND
        (:q IS NULL OR LOWER(COALESCE(e.description, '')) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<Expense> searchByDriver(@Param("driverId") Long driverId, @Param("branchId") Long branchId,
                                 @Param("organizationId") Long organizationId, @Param("status") ExpenseStatus status,
                                 @Param("vehicleId") Long vehicleId, @Param("q") String q, Pageable pageable);

    List<Expense> findByBranch_Id(Long branchId);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
        WHERE e.status = :status
        AND e.date >= :start AND e.date < :end
        AND (:branchId IS NULL OR e.branch.id = :branchId)
        AND (:organizationId IS NULL OR e.branch.organization.id = :organizationId)
        AND (:category IS NULL OR e.category = :category)
        """)
    Double sumByStatusAndPeriod(@Param("status") ExpenseStatus status,
                                @Param("start") LocalDate start,
                                @Param("end") LocalDate end,
                                @Param("branchId") Long branchId,
                                @Param("organizationId") Long organizationId,
                                @Param("category") ExpenseCategory category);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
        WHERE e.status = :status
        AND e.date >= :start AND e.date < :end
        AND e.category IN :categories
        AND (:branchId IS NULL OR e.branch.id = :branchId)
        AND (:organizationId IS NULL OR e.branch.organization.id = :organizationId)
        """)
    Double sumByStatusPeriodAndCategories(@Param("status") ExpenseStatus status,
                                          @Param("start") LocalDate start,
                                          @Param("end") LocalDate end,
                                          @Param("categories") Collection<ExpenseCategory> categories,
                                          @Param("branchId") Long branchId,
                                          @Param("organizationId") Long organizationId);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
        WHERE e.status = :status
        AND e.date >= :start AND e.date < :end
        AND e.category NOT IN :exclude
        AND (:branchId IS NULL OR e.branch.id = :branchId)
        AND (:organizationId IS NULL OR e.branch.organization.id = :organizationId)
        """)
    Double sumByStatusPeriodExcluding(@Param("status") ExpenseStatus status,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end,
                                      @Param("exclude") Collection<ExpenseCategory> exclude,
                                      @Param("branchId") Long branchId,
                                      @Param("organizationId") Long organizationId);

    @Query("""
        SELECT COUNT(e) FROM Expense e
        WHERE e.status = :status
        AND (:branchId IS NULL OR e.branch.id = :branchId)
        AND (:organizationId IS NULL OR e.branch.organization.id = :organizationId)
        """)
    long countByStatusScoped(@Param("status") ExpenseStatus status,
                             @Param("branchId") Long branchId,
                             @Param("organizationId") Long organizationId);

    @Query("""
        SELECT COUNT(e) FROM Expense e
        WHERE e.status = :status
        AND e.date >= :start AND e.date < :end
        AND (:branchId IS NULL OR e.branch.id = :branchId)
        AND (:organizationId IS NULL OR e.branch.organization.id = :organizationId)
        """)
    long countByStatusAndPeriod(@Param("status") ExpenseStatus status,
                                @Param("start") LocalDate start,
                                @Param("end") LocalDate end,
                                @Param("branchId") Long branchId,
                                @Param("organizationId") Long organizationId);

    @Query("""
        SELECT e FROM Expense e WHERE
        e.date >= :start AND e.date < :end
        AND (:branchId IS NULL OR e.branch.id = :branchId)
        AND (:organizationId IS NULL OR e.branch.organization.id = :organizationId)
        AND (:status IS NULL OR e.status = :status)
        AND (:category IS NULL OR e.category = :category)
        AND (:vehicleId IS NULL OR e.vehicle.id = :vehicleId)
        ORDER BY e.date DESC, e.id DESC
        """)
    Page<Expense> searchPeriod(@Param("start") LocalDate start,
                               @Param("end") LocalDate end,
                               @Param("branchId") Long branchId,
                               @Param("organizationId") Long organizationId,
                               @Param("status") ExpenseStatus status,
                               @Param("category") ExpenseCategory category,
                               @Param("vehicleId") Long vehicleId,
                               Pageable pageable);
}
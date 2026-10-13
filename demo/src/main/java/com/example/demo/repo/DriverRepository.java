package com.example.demo.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.Codes.DriverStatus;
import com.example.demo.domain.Driver;

public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByUserId(Long userId);
    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);
    long countByBranch_Id(Long branchId);
    long countByStatus(DriverStatus status);
    List<Driver> findByBranch_Id(Long branchId);

    @Query("""
        SELECT d FROM Driver d WHERE
        (:branchId IS NULL OR d.branch.id = :branchId) AND
        (:organizationId IS NULL OR d.branch.organization.id = :organizationId) AND
        (:userId IS NULL OR d.userId = :userId) AND
        (:status IS NULL OR d.status = :status) AND
        (:q IS NULL OR LOWER(d.name) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%')
            OR LOWER(d.licenseNumber) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<Driver> search(@Param("branchId") Long branchId, @Param("organizationId") Long organizationId,
                        @Param("userId") Long userId, @Param("status") DriverStatus status,
                        @Param("q") String q, Pageable pageable);
}
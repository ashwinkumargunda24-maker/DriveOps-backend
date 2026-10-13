package com.example.demo.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.Codes.VehicleStatus;
import com.example.demo.domain.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);
    long countByBranch_Id(Long branchId);
    long countByStatus(VehicleStatus status);
    long countByBranch_IdAndStatus(Long branchId, VehicleStatus status);
    List<Vehicle> findByBranch_Id(Long branchId);
    Page<Vehicle> findByBranch_Id(Long branchId, Pageable pageable);

    @Query("""
        SELECT v FROM Vehicle v WHERE
        (:branchId IS NULL OR v.branch.id = :branchId) AND
        (:organizationId IS NULL OR v.branch.organization.id = :organizationId) AND
        (:assignedVehicleId IS NULL OR v.id = :assignedVehicleId) AND
        (:status IS NULL OR v.status = :status) AND
        (:q IS NULL OR LOWER(v.registrationNumber) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(COALESCE(v.model, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<Vehicle> search(@Param("branchId") Long branchId, @Param("organizationId") Long organizationId,
                         @Param("assignedVehicleId") Long assignedVehicleId, @Param("status") VehicleStatus status,
                         @Param("q") String q, Pageable pageable);
}
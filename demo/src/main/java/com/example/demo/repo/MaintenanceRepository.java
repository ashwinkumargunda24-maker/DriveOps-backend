package com.example.demo.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.MaintenanceRecord;
import com.example.demo.domain.Codes.MaintenanceStatus;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, Long> {
    List<MaintenanceRecord> findByVehicle_IdOrderByServiceDateDesc(Long vehicleId);

    @Query("""
        SELECT m FROM MaintenanceRecord m WHERE
        (:branchId IS NULL OR m.vehicle.branch.id = :branchId) AND
        (:organizationId IS NULL OR m.vehicle.branch.organization.id = :organizationId) AND
        (:vehicleId IS NULL OR m.vehicle.id = :vehicleId) AND
        (:status IS NULL OR m.status = :status) AND
        (:q IS NULL OR LOWER(m.vehicle.registrationNumber) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<MaintenanceRecord> search(@Param("branchId") Long branchId, @Param("organizationId") Long organizationId,
                                   @Param("vehicleId") Long vehicleId, @Param("status") MaintenanceStatus status,
                                   @Param("q") String q, Pageable pageable);
}
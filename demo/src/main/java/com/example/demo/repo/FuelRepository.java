package com.example.demo.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.FuelRecord;

public interface FuelRepository extends JpaRepository<FuelRecord, Long> {
    List<FuelRecord> findByVehicle_IdOrderByDateAsc(Long vehicleId);
    List<FuelRecord> findByDriver_IdOrderByDateDesc(Long driverId);

    @Query("""
        SELECT f FROM FuelRecord f WHERE
        (:branchId IS NULL OR f.vehicle.branch.id = :branchId) AND
        (:organizationId IS NULL OR f.vehicle.branch.organization.id = :organizationId) AND
        (:driverId IS NULL OR f.driver.id = :driverId) AND
        (:q IS NULL OR LOWER(f.vehicle.registrationNumber) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<FuelRecord> search(@Param("branchId") Long branchId, @Param("organizationId") Long organizationId,
                            @Param("driverId") Long driverId, @Param("q") String q, Pageable pageable);
}
package com.example.demo.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.Incident;
import com.example.demo.domain.Codes.IncidentSeverity;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByDriver_IdOrderByOccurredAtDesc(Long driverId);

    @Query("""
        SELECT i FROM Incident i WHERE
        (:branchId IS NULL OR i.branch.id = :branchId) AND
        (:organizationId IS NULL OR i.branch.organization.id = :organizationId) AND
        (:severity IS NULL OR i.severity = :severity) AND
        (:driverId IS NULL OR i.driver.id = :driverId) AND
        (:q IS NULL OR LOWER(COALESCE(i.description, '')) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<Incident> search(@Param("branchId") Long branchId, @Param("organizationId") Long organizationId,
                          @Param("severity") IncidentSeverity severity, @Param("driverId") Long driverId,
                          @Param("q") String q, Pageable pageable);
}
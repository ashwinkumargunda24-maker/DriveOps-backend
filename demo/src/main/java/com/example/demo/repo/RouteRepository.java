package com.example.demo.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.Route;

public interface RouteRepository extends JpaRepository<Route, Long> {
    List<Route> findByBranch_Id(Long branchId);

    @Query("""
        SELECT r FROM Route r WHERE
        (:branchId IS NULL OR r.branch.id = :branchId) AND
        (:organizationId IS NULL OR r.branch.organization.id = :organizationId) AND
        (:driverId IS NULL OR EXISTS (SELECT t.id FROM Trip t WHERE t.route = r AND t.driver.id = :driverId)) AND
        (:q IS NULL OR LOWER(r.source) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%')
            OR LOWER(r.destination) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<Route> search(@Param("branchId") Long branchId, @Param("organizationId") Long organizationId,
                       @Param("driverId") Long driverId, @Param("q") String q, Pageable pageable);
}
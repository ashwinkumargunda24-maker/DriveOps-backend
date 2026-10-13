package com.example.demo.repo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.Codes.TripStatus;
import com.example.demo.domain.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByDriver_IdOrderByStartDateDesc(Long driverId);
    boolean existsByDriver_IdAndVehicle_Id(Long driverId, Long vehicleId);
    long countByBranch_Id(Long branchId);
    long countByStatus(TripStatus status);
    long countByBranch_IdAndStatus(Long branchId, TripStatus status);

    @Query("""
        SELECT t FROM Trip t WHERE
        (:branchId IS NULL OR t.branch.id = :branchId) AND
        (:organizationId IS NULL OR t.branch.organization.id = :organizationId) AND
        (:status IS NULL OR t.status = :status) AND
        (:driverId IS NULL OR t.driver.id = :driverId) AND
        (:q IS NULL OR CAST(t.id AS string) LIKE CONCAT('%', CAST(:q AS string), '%')
            OR LOWER(COALESCE(t.purpose, '')) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<Trip> search(@Param("branchId") Long branchId, @Param("organizationId") Long organizationId,
                      @Param("status") TripStatus status, @Param("driverId") Long driverId,
                      @Param("q") String q, Pageable pageable);

    @Query("""
        SELECT COUNT(t) FROM Trip t WHERE t.vehicle.id = :vehicleId
        AND t.status NOT IN ('CANCELLED', 'COMPLETED')
        AND t.startDate < :end AND t.expectedEndDate > :start
        AND (:excludeId IS NULL OR t.id <> :excludeId)
        """)
    long countVehicleOverlap(@Param("vehicleId") Long vehicleId, @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

    @Query("""
        SELECT COUNT(t) FROM Trip t WHERE t.driver.id = :driverId
        AND t.status NOT IN ('CANCELLED', 'COMPLETED')
        AND t.startDate < :end AND t.expectedEndDate > :start
        AND (:excludeId IS NULL OR t.id <> :excludeId)
        """)
    long countDriverOverlap(@Param("driverId") Long driverId, @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

    @Query("""
        SELECT COUNT(t) FROM Trip t
        WHERE t.status = :status
        AND COALESCE(t.actualEndDate, t.startDate) >= :start
        AND COALESCE(t.actualEndDate, t.startDate) < :end
        AND (:branchId IS NULL OR t.branch.id = :branchId)
        AND (:organizationId IS NULL OR t.branch.organization.id = :organizationId)
        """)
    long countByStatusCompletedInPeriod(@Param("status") TripStatus status,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("branchId") Long branchId,
                                        @Param("organizationId") Long organizationId);

    @Query("""
        SELECT COALESCE(SUM(t.distanceKm), 0) FROM Trip t
        WHERE t.status = :status
        AND COALESCE(t.actualEndDate, t.startDate) >= :start
        AND COALESCE(t.actualEndDate, t.startDate) < :end
        AND (:branchId IS NULL OR t.branch.id = :branchId)
        AND (:organizationId IS NULL OR t.branch.organization.id = :organizationId)
        """)
    Double sumDistanceByStatusInPeriod(@Param("status") TripStatus status,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end,
                                       @Param("branchId") Long branchId,
                                       @Param("organizationId") Long organizationId);

    @Query("""
        SELECT COALESCE(SUM(t.revenueAmount), 0) FROM Trip t
        WHERE t.status = :status
        AND t.revenueAmount IS NOT NULL
        AND COALESCE(t.actualEndDate, t.startDate) >= :start
        AND COALESCE(t.actualEndDate, t.startDate) < :end
        AND (:branchId IS NULL OR t.branch.id = :branchId)
        AND (:organizationId IS NULL OR t.branch.organization.id = :organizationId)
        """)
    java.math.BigDecimal sumRevenueByStatusInPeriod(@Param("status") TripStatus status,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end,
                                                    @Param("branchId") Long branchId,
                                                    @Param("organizationId") Long organizationId);

    @Query("""
        SELECT COUNT(t) FROM Trip t
        WHERE t.status = :status
        AND t.revenueAmount IS NULL
        AND COALESCE(t.actualEndDate, t.startDate) >= :start
        AND COALESCE(t.actualEndDate, t.startDate) < :end
        AND (:branchId IS NULL OR t.branch.id = :branchId)
        AND (:organizationId IS NULL OR t.branch.organization.id = :organizationId)
        """)
    long countMissingRevenue(@Param("status") TripStatus status,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end,
                             @Param("branchId") Long branchId,
                             @Param("organizationId") Long organizationId);

    @Query("""
        SELECT COUNT(t) FROM Trip t
        WHERE t.createdAt >= :start AND t.createdAt < :end
        AND (:branchId IS NULL OR t.branch.id = :branchId)
        AND (:organizationId IS NULL OR t.branch.organization.id = :organizationId)
        """)
    long countCreatedInPeriod(@Param("start") Instant start,
                              @Param("end") Instant end,
                              @Param("branchId") Long branchId,
                              @Param("organizationId") Long organizationId);

    @Query("""
        SELECT COUNT(DISTINCT h.trip.id) FROM TripStatusHistory h
        WHERE h.newStatus = :delayed
        AND h.changedAt >= :start AND h.changedAt < :end
        AND (:branchId IS NULL OR h.trip.branch.id = :branchId)
        AND (:organizationId IS NULL OR h.trip.branch.organization.id = :organizationId)
        """)
    long countDelayedInPeriod(@Param("delayed") TripStatus delayed,
                              @Param("start") Instant start,
                              @Param("end") Instant end,
                              @Param("branchId") Long branchId,
                              @Param("organizationId") Long organizationId);

    @Query("""
        SELECT t FROM Trip t WHERE
        COALESCE(t.actualEndDate, t.startDate) >= :start
        AND COALESCE(t.actualEndDate, t.startDate) < :end
        AND (:branchId IS NULL OR t.branch.id = :branchId)
        AND (:organizationId IS NULL OR t.branch.organization.id = :organizationId)
        AND (:status IS NULL OR t.status = :status)
        AND (:vehicleId IS NULL OR t.vehicle.id = :vehicleId)
        ORDER BY COALESCE(t.actualEndDate, t.startDate) DESC, t.id DESC
        """)
    Page<Trip> searchPeriod(@Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end,
                            @Param("branchId") Long branchId,
                            @Param("organizationId") Long organizationId,
                            @Param("status") TripStatus status,
                            @Param("vehicleId") Long vehicleId,
                            Pageable pageable);
}
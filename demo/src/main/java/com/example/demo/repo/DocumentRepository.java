package com.example.demo.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.FleetDocument;
import com.example.demo.domain.Codes.DocumentOwnerType;

public interface DocumentRepository extends JpaRepository<FleetDocument, Long> {
    java.util.List<FleetDocument> findByOwnerTypeAndOwnerId(DocumentOwnerType ownerType, Long ownerId);
    Page<FleetDocument> findByBranchId(Long branchId, Pageable pageable);

    @Query("""
        SELECT d FROM FleetDocument d, Branch b WHERE b.id = d.branchId AND
        (:branchId IS NULL OR d.branchId = :branchId) AND
        (:organizationId IS NULL OR b.organization.id = :organizationId)
        """)
    Page<FleetDocument> searchScoped(@Param("branchId") Long branchId,
                                     @Param("organizationId") Long organizationId, Pageable pageable);
}
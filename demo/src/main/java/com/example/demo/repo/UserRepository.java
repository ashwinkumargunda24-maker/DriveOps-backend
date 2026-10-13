package com.example.demo.repo;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.domain.AppUser;
import com.example.demo.domain.Codes.Role;

import java.util.List;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    @EntityGraph(attributePaths = {"branch", "organization"})
    Optional<AppUser> findWithAssociationsById(Long id);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    Page<AppUser> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String u, String e, Pageable pageable);

    @Query("""
        SELECT u FROM AppUser u WHERE
        (:organizationId IS NULL OR u.organization.id = :organizationId) AND
        (:branchId IS NULL OR u.branch.id = :branchId) AND
        (:q IS NULL OR LOWER(u.username) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%')
            OR LOWER(u.email) LIKE CONCAT('%', LOWER(CAST(:q AS string)), '%'))
        """)
    Page<AppUser> searchScoped(@Param("organizationId") Long organizationId, @Param("branchId") Long branchId,
                               @Param("q") String q, Pageable pageable);

    List<AppUser> findByRoleAndActiveTrue(Role role);
}
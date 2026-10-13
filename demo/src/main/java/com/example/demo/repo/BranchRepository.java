package com.example.demo.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.domain.Branch;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByOrganization_Id(Long organizationId);
    Page<Branch> findByNameContainingIgnoreCase(String q, Pageable pageable);
    Page<Branch> findByOrganization_IdAndNameContainingIgnoreCase(Long organizationId, String q, Pageable pageable);
}
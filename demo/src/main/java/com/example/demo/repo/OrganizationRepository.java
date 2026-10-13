package com.example.demo.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.domain.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}
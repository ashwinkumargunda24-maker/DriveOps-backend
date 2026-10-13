package com.example.demo.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.example.demo.domain.Codes.DocumentKind;
import com.example.demo.domain.Codes.DocumentOwnerType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "fleet_documents")
public class FleetDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private DocumentOwnerType ownerType;
    private Long ownerId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    private Branch branch;
    @Column(name = "branch_id")
    private Long branchId;
    @Enumerated(EnumType.STRING)
    private DocumentKind kind;
    private String fileName;
    private String storedPath;
    private LocalDate expiryDate;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public DocumentOwnerType getOwnerType() { return ownerType; }
    public void setOwnerType(DocumentOwnerType ownerType) { this.ownerType = ownerType; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Branch getBranch() { return branch; }
    public DocumentKind getKind() { return kind; }
    public void setKind(DocumentKind kind) { this.kind = kind; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public Instant getCreatedAt() { return createdAt; }
}

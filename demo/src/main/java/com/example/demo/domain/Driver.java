package com.example.demo.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.example.demo.domain.Codes.DriverStatus;

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
@Table(name = "drivers")
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String phone;
    private String email;
    private String address;
    @Column(nullable = false, unique = true)
    private String licenseNumber;
    private String licenseType;
    private LocalDate licenseExpiry;
    private LocalDate joiningDate;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Branch branch;
    @Enumerated(EnumType.STRING)
    private DriverStatus status = DriverStatus.AVAILABLE;
    @Column(name = "user_id")
    private Long userId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private AppUser user;
    @Column(name = "assigned_vehicle_id")
    private Long assignedVehicleId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_vehicle_id", insertable = false, updatable = false)
    private Vehicle assignedVehicle;
    private boolean active = true;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public LocalDate getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(LocalDate licenseExpiry) { this.licenseExpiry = licenseExpiry; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public DriverStatus getStatus() { return status; }
    public void setStatus(DriverStatus status) { this.status = status; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public AppUser getUser() { return user; }
    public Long getAssignedVehicleId() { return assignedVehicleId; }
    public void setAssignedVehicleId(Long assignedVehicleId) { this.assignedVehicleId = assignedVehicleId; }
    public Vehicle getAssignedVehicle() { return assignedVehicle; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}

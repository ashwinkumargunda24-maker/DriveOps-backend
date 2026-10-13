package com.example.demo.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.example.demo.domain.Codes.VehicleStatus;

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
@Table(name = "fleet_vehicles")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String registrationNumber;
    private String vehicleType;
    private String manufacturer;
    private String model;
    private Integer year;
    private String fuelType;
    private Integer capacity;
    private Integer currentMileage;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Branch branch;
    @Enumerated(EnumType.STRING)
    private VehicleStatus status = VehicleStatus.AVAILABLE;
    private LocalDate insuranceExpiry;
    private LocalDate registrationExpiry;
    private LocalDate pollutionExpiry;
    @Column(name = "assigned_driver_id")
    private Long assignedDriverId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_driver_id", insertable = false, updatable = false)
    private Driver assignedDriver;
    private boolean active = true;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getCurrentMileage() { return currentMileage; }
    public void setCurrentMileage(Integer currentMileage) { this.currentMileage = currentMileage; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public LocalDate getInsuranceExpiry() { return insuranceExpiry; }
    public void setInsuranceExpiry(LocalDate insuranceExpiry) { this.insuranceExpiry = insuranceExpiry; }
    public LocalDate getRegistrationExpiry() { return registrationExpiry; }
    public void setRegistrationExpiry(LocalDate registrationExpiry) { this.registrationExpiry = registrationExpiry; }
    public LocalDate getPollutionExpiry() { return pollutionExpiry; }
    public void setPollutionExpiry(LocalDate pollutionExpiry) { this.pollutionExpiry = pollutionExpiry; }
    public Long getAssignedDriverId() { return assignedDriverId; }
    public void setAssignedDriverId(Long assignedDriverId) { this.assignedDriverId = assignedDriverId; }
    public Driver getAssignedDriver() { return assignedDriver; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}

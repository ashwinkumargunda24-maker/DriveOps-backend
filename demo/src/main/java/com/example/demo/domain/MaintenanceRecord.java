package com.example.demo.domain;

import java.time.Instant;
import java.time.LocalDate;

import com.example.demo.domain.Codes.MaintenanceStatus;
import com.example.demo.domain.Codes.MaintenanceType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "maintenance_records")
public class MaintenanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Vehicle vehicle;
    @Enumerated(EnumType.STRING)
    private MaintenanceType serviceType;
    private LocalDate serviceDate;
    private Integer currentMileage;
    private Double serviceCost;
    private LocalDate nextServiceDate;
    private Integer nextServiceMileage;
    private String description;
    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status = MaintenanceStatus.SCHEDULED;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public MaintenanceType getServiceType() { return serviceType; }
    public void setServiceType(MaintenanceType serviceType) { this.serviceType = serviceType; }
    public LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(LocalDate serviceDate) { this.serviceDate = serviceDate; }
    public Integer getCurrentMileage() { return currentMileage; }
    public void setCurrentMileage(Integer currentMileage) { this.currentMileage = currentMileage; }
    public Double getServiceCost() { return serviceCost; }
    public void setServiceCost(Double serviceCost) { this.serviceCost = serviceCost; }
    public LocalDate getNextServiceDate() { return nextServiceDate; }
    public void setNextServiceDate(LocalDate nextServiceDate) { this.nextServiceDate = nextServiceDate; }
    public Integer getNextServiceMileage() { return nextServiceMileage; }
    public void setNextServiceMileage(Integer nextServiceMileage) { this.nextServiceMileage = nextServiceMileage; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public MaintenanceStatus getStatus() { return status; }
    public void setStatus(MaintenanceStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.example.demo.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import com.example.demo.domain.Codes.TripStatus;

import jakarta.persistence.Column;
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
@Table(name = "trips")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY)
    private Driver driver;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Route route;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Branch branch;
    private LocalDateTime startDate;
    private LocalDateTime expectedEndDate;
    private LocalDateTime actualEndDate;
    private String purpose;
    private Double distanceKm;
    @Column(precision = 14, scale = 2)
    private BigDecimal revenueAmount;
    private String notes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status = TripStatus.PLANNED;
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
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public Route getRoute() { return route; }
    public void setRoute(Route route) { this.route = route; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getExpectedEndDate() { return expectedEndDate; }
    public void setExpectedEndDate(LocalDateTime expectedEndDate) { this.expectedEndDate = expectedEndDate; }
    public LocalDateTime getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(LocalDateTime actualEndDate) { this.actualEndDate = actualEndDate; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public BigDecimal getRevenueAmount() { return revenueAmount; }
    public void setRevenueAmount(BigDecimal revenueAmount) { this.revenueAmount = revenueAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}

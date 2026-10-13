package com.example.demo.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "fuel_records")
public class FuelRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY)
    private Driver driver;
    private LocalDate date;
    private Integer odometer;
    private String fuelType;
    private Double quantity;
    private Double pricePerLiter;
    private Double totalCost;
    private String fuelStation;
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (totalCost == null && quantity != null && pricePerLiter != null) {
            totalCost = quantity * pricePerLiter;
        }
    }

    public Long getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Integer getOdometer() { return odometer; }
    public void setOdometer(Integer odometer) { this.odometer = odometer; }
    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public Double getPricePerLiter() { return pricePerLiter; }
    public void setPricePerLiter(Double pricePerLiter) { this.pricePerLiter = pricePerLiter; }
    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }
    public String getFuelStation() { return fuelStation; }
    public void setFuelStation(String fuelStation) { this.fuelStation = fuelStation; }
    public Instant getCreatedAt() { return createdAt; }
}

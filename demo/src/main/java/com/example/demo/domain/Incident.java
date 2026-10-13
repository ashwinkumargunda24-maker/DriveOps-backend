package com.example.demo.domain;

import java.time.Instant;
import java.time.LocalDateTime;

import com.example.demo.domain.Codes.IncidentSeverity;
import com.example.demo.domain.Codes.IncidentStatus;

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
@Table(name = "incidents")
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY)
    private Driver driver;
    @ManyToOne(fetch = FetchType.LAZY)
    private Trip trip;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Branch branch;
    private LocalDateTime occurredAt;
    private String location;
    private String incidentType;
    @Enumerated(EnumType.STRING)
    private IncidentSeverity severity;
    private String description;
    private String evidencePath;
    private String investigationNotes;
    private String assignedInvestigator;
    private String resolutionNotes;
    @Enumerated(EnumType.STRING)
    private IncidentStatus status = IncidentStatus.REPORTED;
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
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }
    public IncidentSeverity getSeverity() { return severity; }
    public void setSeverity(IncidentSeverity severity) { this.severity = severity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEvidencePath() { return evidencePath; }
    public void setEvidencePath(String evidencePath) { this.evidencePath = evidencePath; }
    public String getInvestigationNotes() { return investigationNotes; }
    public void setInvestigationNotes(String investigationNotes) { this.investigationNotes = investigationNotes; }
    public String getAssignedInvestigator() { return assignedInvestigator; }
    public void setAssignedInvestigator(String assignedInvestigator) { this.assignedInvestigator = assignedInvestigator; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}

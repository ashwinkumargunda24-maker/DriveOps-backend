package com.example.demo.domain;

import java.time.Instant;

import com.example.demo.domain.Codes.TripStatus;

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
@Table(name = "trip_status_history")
public class TripStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Trip trip;
    @Enumerated(EnumType.STRING)
    private TripStatus oldStatus;
    @Enumerated(EnumType.STRING)
    private TripStatus newStatus;
    private String changedBy;
    private Instant changedAt;
    private String remarks;

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public TripStatus getOldStatus() { return oldStatus; }
    public void setOldStatus(TripStatus oldStatus) { this.oldStatus = oldStatus; }
    public TripStatus getNewStatus() { return newStatus; }
    public void setNewStatus(TripStatus newStatus) { this.newStatus = newStatus; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public Instant getChangedAt() { return changedAt; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}

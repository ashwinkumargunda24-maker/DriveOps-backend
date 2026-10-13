package com.example.demo.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.domain.Codes.MonthlyReportStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "monthly_reports", uniqueConstraints = @UniqueConstraint(name = "uk_monthly_report_period",
        columnNames = { "reporting_year", "reporting_month" }))
public class MonthlyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "reporting_year", nullable = false)
    private int reportingYear;
    @Column(name = "reporting_month", nullable = false)
    private int reportingMonth;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MonthlyReportStatus status = MonthlyReportStatus.GENERATING;
    private Instant generatedAt;
    private Instant lastRegeneratedAt;
    private int snapshotVersion = 1;
    private String generatedBy;
    private String failureMessage;
    private long tripsCompleted;
    private long tripsCreated;
    private long tripsCancelled;
    private long tripsDelayed;
    @Column(precision = 14, scale = 2)
    private BigDecimal totalDistanceKm = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal totalExpenses = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal fuelExpenses = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal maintenanceExpenses = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal otherExpenses = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal netProfit;
    private boolean profitAvailable;
    private boolean revenueComplete;
    private long pendingExpenseApprovals;
    private long pendingExpenseApprovalsInMonth;
    @Column(precision = 14, scale = 4)
    private BigDecimal costPerKm;
    private Double utilizationPercent;
    private boolean notificationSent;
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MonthlyReportBranch> branches = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public int getReportingYear() { return reportingYear; }
    public void setReportingYear(int reportingYear) { this.reportingYear = reportingYear; }
    public int getReportingMonth() { return reportingMonth; }
    public void setReportingMonth(int reportingMonth) { this.reportingMonth = reportingMonth; }
    public MonthlyReportStatus getStatus() { return status; }
    public void setStatus(MonthlyReportStatus status) { this.status = status; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Instant getLastRegeneratedAt() { return lastRegeneratedAt; }
    public void setLastRegeneratedAt(Instant lastRegeneratedAt) { this.lastRegeneratedAt = lastRegeneratedAt; }
    public int getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(int snapshotVersion) { this.snapshotVersion = snapshotVersion; }
    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }
    public long getTripsCompleted() { return tripsCompleted; }
    public void setTripsCompleted(long tripsCompleted) { this.tripsCompleted = tripsCompleted; }
    public long getTripsCreated() { return tripsCreated; }
    public void setTripsCreated(long tripsCreated) { this.tripsCreated = tripsCreated; }
    public long getTripsCancelled() { return tripsCancelled; }
    public void setTripsCancelled(long tripsCancelled) { this.tripsCancelled = tripsCancelled; }
    public long getTripsDelayed() { return tripsDelayed; }
    public void setTripsDelayed(long tripsDelayed) { this.tripsDelayed = tripsDelayed; }
    public BigDecimal getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(BigDecimal totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }
    public BigDecimal getFuelExpenses() { return fuelExpenses; }
    public void setFuelExpenses(BigDecimal fuelExpenses) { this.fuelExpenses = fuelExpenses; }
    public BigDecimal getMaintenanceExpenses() { return maintenanceExpenses; }
    public void setMaintenanceExpenses(BigDecimal maintenanceExpenses) { this.maintenanceExpenses = maintenanceExpenses; }
    public BigDecimal getOtherExpenses() { return otherExpenses; }
    public void setOtherExpenses(BigDecimal otherExpenses) { this.otherExpenses = otherExpenses; }
    public BigDecimal getNetProfit() { return netProfit; }
    public void setNetProfit(BigDecimal netProfit) { this.netProfit = netProfit; }
    public boolean isProfitAvailable() { return profitAvailable; }
    public void setProfitAvailable(boolean profitAvailable) { this.profitAvailable = profitAvailable; }
    public boolean isRevenueComplete() { return revenueComplete; }
    public void setRevenueComplete(boolean revenueComplete) { this.revenueComplete = revenueComplete; }
    public long getPendingExpenseApprovals() { return pendingExpenseApprovals; }
    public void setPendingExpenseApprovals(long pendingExpenseApprovals) { this.pendingExpenseApprovals = pendingExpenseApprovals; }
    public long getPendingExpenseApprovalsInMonth() { return pendingExpenseApprovalsInMonth; }
    public void setPendingExpenseApprovalsInMonth(long pendingExpenseApprovalsInMonth) { this.pendingExpenseApprovalsInMonth = pendingExpenseApprovalsInMonth; }
    public BigDecimal getCostPerKm() { return costPerKm; }
    public void setCostPerKm(BigDecimal costPerKm) { this.costPerKm = costPerKm; }
    public Double getUtilizationPercent() { return utilizationPercent; }
    public void setUtilizationPercent(Double utilizationPercent) { this.utilizationPercent = utilizationPercent; }
    public boolean isNotificationSent() { return notificationSent; }
    public void setNotificationSent(boolean notificationSent) { this.notificationSent = notificationSent; }
    public List<MonthlyReportBranch> getBranches() { return branches; }
}

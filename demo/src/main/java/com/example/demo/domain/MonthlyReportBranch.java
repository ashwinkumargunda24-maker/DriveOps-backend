package com.example.demo.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "monthly_report_branches")
public class MonthlyReportBranch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MonthlyReport report;
    private Long branchId;
    private String branchName;
    private String organizationName;
    private long tripsCompleted;
    @Column(precision = 14, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal expenses = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal fuelExpenses = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal maintenanceExpenses = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal otherExpenses = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2)
    private BigDecimal netProfit;
    private boolean profitAvailable;
    @Column(precision = 14, scale = 2)
    private BigDecimal distanceKm = BigDecimal.ZERO;

    public Long getId() { return id; }
    public MonthlyReport getReport() { return report; }
    public void setReport(MonthlyReport report) { this.report = report; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public long getTripsCompleted() { return tripsCompleted; }
    public void setTripsCompleted(long tripsCompleted) { this.tripsCompleted = tripsCompleted; }
    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    public BigDecimal getExpenses() { return expenses; }
    public void setExpenses(BigDecimal expenses) { this.expenses = expenses; }
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
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
}

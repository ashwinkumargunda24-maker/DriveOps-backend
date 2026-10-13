package com.example.demo.repo;

import org.springframework.stereotype.Component;

@Component
public class Repos {
    public final OrganizationRepository organizations;
    public final BranchRepository branches;
    public final UserRepository users;
    public final VehicleRepository vehicles;
    public final DriverRepository drivers;
    public final RouteRepository routes;
    public final TripRepository trips;
    public final TripStatusHistoryRepository tripHistory;
    public final MaintenanceRepository maintenance;
    public final FuelRepository fuel;
    public final ExpenseRepository expenses;
    public final IncidentRepository incidents;
    public final DocumentRepository documents;
    public final NotificationRepository notifications;
    public final AuditLogRepository audits;
    public final MonthlyReportRepository monthlyReports;

    public Repos(OrganizationRepository organizations, BranchRepository branches, UserRepository users,
                 VehicleRepository vehicles, DriverRepository drivers, RouteRepository routes,
                 TripRepository trips, TripStatusHistoryRepository tripHistory, MaintenanceRepository maintenance,
                 FuelRepository fuel, ExpenseRepository expenses, IncidentRepository incidents,
                 DocumentRepository documents, NotificationRepository notifications, AuditLogRepository audits,
                 MonthlyReportRepository monthlyReports) {
        this.organizations = organizations;
        this.branches = branches;
        this.users = users;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.routes = routes;
        this.trips = trips;
        this.tripHistory = tripHistory;
        this.maintenance = maintenance;
        this.fuel = fuel;
        this.expenses = expenses;
        this.incidents = incidents;
        this.documents = documents;
        this.notifications = notifications;
        this.audits = audits;
        this.monthlyReports = monthlyReports;
    }
}

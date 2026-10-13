package com.example.demo.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.AppNotification;
import com.example.demo.domain.AppUser;
import com.example.demo.domain.Codes.MonthlyReportStatus;
import com.example.demo.domain.Codes.Role;
import com.example.demo.domain.MonthlyReport;
import com.example.demo.domain.MonthlyReportBranch;
import com.example.demo.finance.Money;
import com.example.demo.repo.Repos;
import com.example.demo.security.AccessGuard;
import com.example.demo.web.ApiException;

@Service
public class MonthEndReportService {
    private static final Logger log = LoggerFactory.getLogger(MonthEndReportService.class);

    private final Repos repos;
    private final AccessGuard access;
    private final MonthlyFinanceService finance;
    private final Clock clock;

    public MonthEndReportService(Repos repos, AccessGuard access, MonthlyFinanceService finance, Clock clock) {
        this.repos = repos;
        this.access = access;
        this.finance = finance;
        this.clock = clock;
    }

    public YearMonth previousCompletedMonth() {
        return YearMonth.now(clock).minusMonths(1);
    }

    @Transactional
    public MonthlyReport generatePreviousMonthIfAbsent() {
        return generate(previousCompletedMonth(), false, "scheduler");
    }

    @Transactional
    public Map<String, Object> generateAuthorized(Integer year, Integer month, boolean regenerate) {
        access.require(Role.SUPER_ADMIN);
        YearMonth period = year == null || month == null ? previousCompletedMonth() : finance.parsePeriod(year, month);
        MonthlyReport report = generate(period, regenerate, access.user().getUsername());
        return toMap(report, true);
    }

    @Transactional
    public MonthlyReport generate(YearMonth period, boolean regenerate, String actor) {
        MonthlyReport existing = repos.monthlyReports.findByReportingYearAndReportingMonth(period.getYear(), period.getMonthValue())
                .orElse(null);
        if (existing != null && existing.getStatus() == MonthlyReportStatus.FINALIZED && !regenerate) {
            return existing;
        }
        if (existing != null && existing.getStatus() == MonthlyReportStatus.GENERATING && !regenerate) {
            return existing;
        }
        MonthlyReport report = existing == null ? new MonthlyReport() : existing;
        if (existing == null) {
            report.setReportingYear(period.getYear());
            report.setReportingMonth(period.getMonthValue());
            report.setStatus(MonthlyReportStatus.GENERATING);
            report.setGeneratedBy(actor);
            try {
                repos.monthlyReports.saveAndFlush(report);
            } catch (DataIntegrityViolationException duplicate) {
                log.info("Monthly report for {} already exists; skipping duplicate insert.", period);
                return repos.monthlyReports.findByReportingYearAndReportingMonth(period.getYear(), period.getMonthValue())
                        .orElseThrow();
            }
        } else if (regenerate && existing.getStatus() == MonthlyReportStatus.FINALIZED) {
            report.setSnapshotVersion(existing.getSnapshotVersion() + 1);
            report.setLastRegeneratedAt(Instant.now(clock));
            report.setGeneratedBy(actor);
            report.setStatus(MonthlyReportStatus.GENERATING);
            report.getBranches().clear();
            report.setNotificationSent(false);
            repos.monthlyReports.save(report);
        }
        try {
            persistSnapshot(report, period, actor);
            notifySuperAdmins(report);
            return report;
        } catch (RuntimeException ex) {
            log.error("Monthly report generation failed for {}", period, ex);
            report.setStatus(MonthlyReportStatus.FAILED);
            report.setFailureMessage(ex.getMessage() == null ? "Report generation failed." : ex.getMessage());
            repos.monthlyReports.save(report);
            throw ex;
        }
    }

    public List<Map<String, Object>> listAuthorized() {
        access.require(Role.SUPER_ADMIN, Role.FINANCE_OFFICER);
        return repos.monthlyReports.findAllByOrderByReportingYearDescReportingMonthDesc()
                .stream().map(report -> toMap(report, false)).toList();
    }

    public Map<String, Object> detailAuthorized(Long id) {
        access.require(Role.SUPER_ADMIN, Role.FINANCE_OFFICER);
        MonthlyReport report = repos.monthlyReports.findWithBranchesById(id)
                .orElseThrow(() -> new ApiException(404, "Monthly report not found."));
        Map<String, Object> body = toMap(report, true);
        YearMonth period = YearMonth.of(report.getReportingYear(), report.getReportingMonth());
        if (report.getStatus() == MonthlyReportStatus.FINALIZED) {
            Long organizationId = access.is(Role.FINANCE_OFFICER) ? access.organizationScopeId() : null;
            body.put("liveComparison", finance.summaryWithComparison(period, organizationId, null).get("comparison"));
        }
        return body;
    }

    public String exportAuthorized(Long id) {
        access.require(Role.SUPER_ADMIN, Role.FINANCE_OFFICER);
        MonthlyReport report = repos.monthlyReports.findWithBranchesById(id)
                .orElseThrow(() -> new ApiException(404, "Monthly report not found."));
        if (access.is(Role.FINANCE_OFFICER)) {
            return exportOrganizationReport(report);
        }
        StringBuilder csv = new StringBuilder();
        csv.append("period,status,version,generatedAt,completedTrips,createdTrips,cancelledTrips,delayedTrips,")
                .append("revenue,expenses,fuel,maintenance,other,profit,profitAvailable,distanceKm,costPerKm,pendingApprovals\n");
        csv.append(report.getReportingYear()).append('-').append(String.format("%02d", report.getReportingMonth())).append(',')
                .append(report.getStatus()).append(',')
                .append(report.getSnapshotVersion()).append(',')
                .append(report.getGeneratedAt()).append(',')
                .append(report.getTripsCompleted()).append(',')
                .append(report.getTripsCreated()).append(',')
                .append(report.getTripsCancelled()).append(',')
                .append(report.getTripsDelayed()).append(',')
                .append(report.getTotalRevenue()).append(',')
                .append(report.getTotalExpenses()).append(',')
                .append(report.getFuelExpenses()).append(',')
                .append(report.getMaintenanceExpenses()).append(',')
                .append(report.getOtherExpenses()).append(',')
                .append(report.getNetProfit()).append(',')
                .append(report.isProfitAvailable()).append(',')
                .append(report.getTotalDistanceKm()).append(',')
                .append(report.getCostPerKm()).append(',')
                .append(report.getPendingExpenseApprovals()).append('\n');
        csv.append('\n').append("branch,organization,completedTrips,revenue,expenses,fuel,maintenance,other,profit,distanceKm\n");
        for (MonthlyReportBranch branch : report.getBranches()) {
            csv.append(csv(branch.getBranchName())).append(',')
                    .append(csv(branch.getOrganizationName())).append(',')
                    .append(branch.getTripsCompleted()).append(',')
                    .append(branch.getRevenue()).append(',')
                    .append(branch.getExpenses()).append(',')
                    .append(branch.getFuelExpenses()).append(',')
                    .append(branch.getMaintenanceExpenses()).append(',')
                    .append(branch.getOtherExpenses()).append(',')
                    .append(branch.getNetProfit()).append(',')
                    .append(branch.getDistanceKm()).append('\n');
        }
        return csv.toString();
    }

    private void persistSnapshot(MonthlyReport report, YearMonth period, String actor) {
        Map<String, Object> summary = finance.summarize(period, null, null);
        report.setGeneratedAt(report.getGeneratedAt() == null ? Instant.now(clock) : report.getGeneratedAt());
        report.setGeneratedBy(actor);
        report.setFailureMessage(null);
        report.setTripsCompleted(((Number) summary.get("completedTrips")).longValue());
        report.setTripsCreated(((Number) summary.get("createdTrips")).longValue());
        report.setTripsCancelled(((Number) summary.get("cancelledTrips")).longValue());
        report.setTripsDelayed(((Number) summary.get("delayedTrips")).longValue());
        report.setTotalDistanceKm((BigDecimal) summary.get("totalDistanceKm"));
        report.setTotalRevenue((BigDecimal) summary.get("totalRevenue"));
        report.setTotalExpenses((BigDecimal) summary.get("totalExpenses"));
        report.setFuelExpenses((BigDecimal) summary.get("fuelExpenses"));
        report.setMaintenanceExpenses((BigDecimal) summary.get("maintenanceExpenses"));
        report.setOtherExpenses((BigDecimal) summary.get("otherExpenses"));
        report.setNetProfit((BigDecimal) summary.get("netProfit"));
        report.setProfitAvailable(Boolean.TRUE.equals(summary.get("profitAvailable")));
        report.setRevenueComplete(Boolean.TRUE.equals(summary.get("revenueComplete")));
        report.setPendingExpenseApprovals(((Number) summary.get("pendingExpenseApprovals")).longValue());
        report.setPendingExpenseApprovalsInMonth(((Number) summary.get("pendingExpenseApprovalsInMonth")).longValue());
        report.setCostPerKm((BigDecimal) summary.get("costPerKm"));
        report.setUtilizationPercent((Double) summary.get("utilizationPercent"));
        report.getBranches().clear();
        for (Map<String, Object> branchRow : finance.branchBreakdown(period, null)) {
            MonthlyReportBranch branch = new MonthlyReportBranch();
            branch.setReport(report);
            branch.setBranchId((Long) branchRow.get("branchId"));
            branch.setBranchName((String) branchRow.get("branchName"));
            branch.setOrganizationName((String) branchRow.get("organizationName"));
            branch.setTripsCompleted(((Number) branchRow.get("completedTrips")).longValue());
            branch.setRevenue((BigDecimal) branchRow.get("totalRevenue"));
            branch.setExpenses((BigDecimal) branchRow.get("totalExpenses"));
            branch.setFuelExpenses((BigDecimal) branchRow.get("fuelExpenses"));
            branch.setMaintenanceExpenses((BigDecimal) branchRow.get("maintenanceExpenses"));
            branch.setOtherExpenses((BigDecimal) branchRow.get("otherExpenses"));
            branch.setNetProfit((BigDecimal) branchRow.get("netProfit"));
            branch.setProfitAvailable(Boolean.TRUE.equals(branchRow.get("profitAvailable")));
            branch.setDistanceKm((BigDecimal) branchRow.get("totalDistanceKm"));
            report.getBranches().add(branch);
        }
        report.setStatus(MonthlyReportStatus.FINALIZED);
        repos.monthlyReports.save(report);
    }

    private void notifySuperAdmins(MonthlyReport report) {
        if (report.getStatus() != MonthlyReportStatus.FINALIZED || report.isNotificationSent()) {
            return;
        }
        String period = report.getReportingYear() + "-" + String.format("%02d", report.getReportingMonth());
        String title = "Month-end report ready: " + period;
        String profitText = report.isProfitAvailable() ? String.valueOf(report.getNetProfit()) : "unavailable";
        String message = "Completed trips: " + report.getTripsCompleted()
                + ". Revenue: " + report.getTotalRevenue()
                + ". Expenses: " + report.getTotalExpenses()
                + ". Net profit: " + profitText
                + ". Open Monthly Reports in the dashboard to view the snapshot.";
        for (AppUser admin : repos.users.findByRoleAndActiveTrue(Role.SUPER_ADMIN)) {
            if (repos.notifications.existsByUserIdAndTypeAndTitle(admin.getId(), "MONTHLY_REPORT", title)) {
                continue;
            }
            AppNotification notification = new AppNotification();
            notification.setUserId(admin.getId());
            notification.setType("MONTHLY_REPORT");
            notification.setTitle(title);
            notification.setMessage(message);
            repos.notifications.save(notification);
        }
        report.setNotificationSent(true);
        repos.monthlyReports.save(report);
    }

    private Map<String, Object> toMap(MonthlyReport report, boolean includeBranches) {
        boolean organizationScoped = access.is(Role.FINANCE_OFFICER);
        List<MonthlyReportBranch> visibleBranches = organizationScoped
                ? visibleBranches(report) : report.getBranches();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", report.getId());
        map.put("period", report.getReportingYear() + "-" + String.format("%02d", report.getReportingMonth()));
        map.put("year", report.getReportingYear());
        map.put("month", report.getReportingMonth());
        map.put("status", report.getStatus().name());
        map.put("generatedAt", report.getGeneratedAt());
        map.put("lastRegeneratedAt", report.getLastRegeneratedAt());
        map.put("snapshotVersion", report.getSnapshotVersion());
        map.put("tripsCompleted", organizationScoped ? visibleBranches.stream()
                .mapToLong(MonthlyReportBranch::getTripsCompleted).sum() : report.getTripsCompleted());
        map.put("totalDistanceKm", organizationScoped
                ? sum(visibleBranches, MonthlyReportBranch::getDistanceKm) : report.getTotalDistanceKm());
        map.put("totalRevenue", organizationScoped
                ? sum(visibleBranches, MonthlyReportBranch::getRevenue) : report.getTotalRevenue());
        map.put("totalExpenses", organizationScoped
                ? sum(visibleBranches, MonthlyReportBranch::getExpenses) : report.getTotalExpenses());
        map.put("fuelExpenses", organizationScoped
                ? sum(visibleBranches, MonthlyReportBranch::getFuelExpenses) : report.getFuelExpenses());
        map.put("maintenanceExpenses", organizationScoped
                ? sum(visibleBranches, MonthlyReportBranch::getMaintenanceExpenses) : report.getMaintenanceExpenses());
        map.put("otherExpenses", organizationScoped
                ? sum(visibleBranches, MonthlyReportBranch::getOtherExpenses) : report.getOtherExpenses());
        boolean profitAvailable = organizationScoped
                ? visibleBranches.stream().allMatch(MonthlyReportBranch::isProfitAvailable) : report.isProfitAvailable();
        map.put("netProfit", profitAvailable
                ? organizationScoped ? sum(visibleBranches, MonthlyReportBranch::getNetProfit) : report.getNetProfit()
                : null);
        map.put("profitAvailable", profitAvailable);
        map.put("revenueComplete", profitAvailable);
        map.put("costPerKm", organizationScoped
                ? costPerKm(sum(visibleBranches, MonthlyReportBranch::getExpenses),
                        sum(visibleBranches, MonthlyReportBranch::getDistanceKm))
                : report.getCostPerKm());
        if (!organizationScoped) {
            map.put("generatedBy", report.getGeneratedBy());
            map.put("failureMessage", report.getFailureMessage());
            map.put("tripsCreated", report.getTripsCreated());
            map.put("tripsCancelled", report.getTripsCancelled());
            map.put("tripsDelayed", report.getTripsDelayed());
            map.put("pendingExpenseApprovals", report.getPendingExpenseApprovals());
            map.put("pendingExpenseApprovalsInMonth", report.getPendingExpenseApprovalsInMonth());
            map.put("utilizationPercent", report.getUtilizationPercent());
        }
        map.put("emailConfigured", false);
        map.put("emailNote", "No email provider is configured. Super Admins are notified in-app only.");
        if (includeBranches) {
            map.put("branches", visibleBranches.stream().map(branch -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("branchId", branch.getBranchId());
                row.put("branchName", branch.getBranchName());
                row.put("organizationName", branch.getOrganizationName());
                row.put("tripsCompleted", branch.getTripsCompleted());
                row.put("revenue", branch.getRevenue());
                row.put("expenses", branch.getExpenses());
                row.put("fuelExpenses", branch.getFuelExpenses());
                row.put("maintenanceExpenses", branch.getMaintenanceExpenses());
                row.put("otherExpenses", branch.getOtherExpenses());
                row.put("netProfit", branch.getNetProfit());
                row.put("profitAvailable", branch.isProfitAvailable());
                row.put("distanceKm", branch.getDistanceKm());
                return row;
            }).toList());
        }
        if (organizationScoped) {
            return map;
        }
        YearMonth previous = YearMonth.of(report.getReportingYear(), report.getReportingMonth()).minusMonths(1);
        repos.monthlyReports.findByReportingYearAndReportingMonth(previous.getYear(), previous.getMonthValue())
                .filter(item -> item.getStatus() == MonthlyReportStatus.FINALIZED)
                .ifPresent(prev -> {
                    Map<String, Object> comparison = new LinkedHashMap<>();
                    comparison.put("previousPeriod", previous.toString());
                    comparison.put("revenueChange", Money.of(report.getTotalRevenue()).subtract(Money.of(prev.getTotalRevenue())));
                    comparison.put("revenueChangePercent", Money.percentChange(Money.of(report.getTotalRevenue()), Money.of(prev.getTotalRevenue())));
                    comparison.put("expensesChange", Money.of(report.getTotalExpenses()).subtract(Money.of(prev.getTotalExpenses())));
                    comparison.put("expensesChangePercent", Money.percentChange(Money.of(report.getTotalExpenses()), Money.of(prev.getTotalExpenses())));
                    if (report.isProfitAvailable() && prev.isProfitAvailable()) {
                        comparison.put("profitChange", Money.of(report.getNetProfit()).subtract(Money.of(prev.getNetProfit())));
                        comparison.put("profitChangePercent", Money.percentChange(Money.of(report.getNetProfit()), Money.of(prev.getNetProfit())));
                    }
                    comparison.put("completedTripsChange", report.getTripsCompleted() - prev.getTripsCompleted());
                    comparison.put("completedTripsChangePercent", Money.percentChange(report.getTripsCompleted(), prev.getTripsCompleted()));
                    map.put("snapshotComparison", comparison);
                });
        return map;
    }

    private List<MonthlyReportBranch> visibleBranches(MonthlyReport report) {
        Long organizationId = access.organizationScopeId();
        Set<Long> branchIds = repos.branches.findByOrganization_Id(organizationId).stream()
                .map(branch -> branch.getId()).collect(java.util.stream.Collectors.toSet());
        return report.getBranches().stream()
                .filter(branch -> branch.getBranchId() != null && branchIds.contains(branch.getBranchId()))
                .toList();
    }

    private BigDecimal sum(List<MonthlyReportBranch> branches, Function<MonthlyReportBranch, BigDecimal> value) {
        return branches.stream().map(value).map(Money::of).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal costPerKm(BigDecimal expenses, BigDecimal distance) {
        return distance.compareTo(BigDecimal.ZERO) > 0
                ? expenses.divide(distance, 4, java.math.RoundingMode.HALF_UP) : null;
    }

    private String exportOrganizationReport(MonthlyReport report) {
        Map<String, Object> scoped = toMap(report, true);
        StringBuilder csv = new StringBuilder();
        csv.append("period,status,version,completedTrips,revenue,expenses,fuel,maintenance,other,")
                .append("profit,profitAvailable,distanceKm,costPerKm\n")
                .append(scoped.get("period")).append(',')
                .append(scoped.get("status")).append(',')
                .append(scoped.get("snapshotVersion")).append(',')
                .append(scoped.get("tripsCompleted")).append(',')
                .append(scoped.get("totalRevenue")).append(',')
                .append(scoped.get("totalExpenses")).append(',')
                .append(scoped.get("fuelExpenses")).append(',')
                .append(scoped.get("maintenanceExpenses")).append(',')
                .append(scoped.get("otherExpenses")).append(',')
                .append(scoped.get("netProfit")).append(',')
                .append(scoped.get("profitAvailable")).append(',')
                .append(scoped.get("totalDistanceKm")).append(',')
                .append(scoped.get("costPerKm")).append('\n');
        csv.append('\n').append("branch,completedTrips,revenue,expenses,fuel,maintenance,other,profit,distanceKm\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) scoped.get("branches");
        for (Map<String, Object> branch : branches) {
            csv.append(csv(String.valueOf(branch.get("branchName")))).append(',')
                    .append(branch.get("tripsCompleted")).append(',')
                    .append(branch.get("revenue")).append(',')
                    .append(branch.get("expenses")).append(',')
                    .append(branch.get("fuelExpenses")).append(',')
                    .append(branch.get("maintenanceExpenses")).append(',')
                    .append(branch.get("otherExpenses")).append(',')
                    .append(branch.get("netProfit")).append(',')
                    .append(branch.get("distanceKm")).append('\n');
        }
        return csv.toString();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\"", "\"\"");
        return text.contains(",") ? "\"" + text + "\"" : text;
    }
}

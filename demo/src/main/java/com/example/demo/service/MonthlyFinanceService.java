package com.example.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.domain.Branch;
import com.example.demo.domain.Codes.ExpenseCategory;
import com.example.demo.domain.Codes.ExpenseStatus;
import com.example.demo.domain.Codes.Role;
import com.example.demo.domain.Codes.TripStatus;
import com.example.demo.domain.Codes.VehicleStatus;
import com.example.demo.domain.Expense;
import com.example.demo.domain.Trip;
import com.example.demo.dto.PageResponse;
import com.example.demo.finance.Money;
import com.example.demo.repo.Repos;
import com.example.demo.security.AccessGuard;
import com.example.demo.web.ApiException;

@Service
@Transactional(readOnly = true)
public class MonthlyFinanceService {
    public static final Set<ExpenseCategory> FUEL_CATEGORIES = EnumSet.of(ExpenseCategory.FUEL);
    public static final Set<ExpenseCategory> MAINTENANCE_CATEGORIES = EnumSet.of(ExpenseCategory.MAINTENANCE, ExpenseCategory.REPAIR);
    public static final Set<ExpenseCategory> LEDGER_EXCLUSIONS_FOR_OTHER = EnumSet.of(
            ExpenseCategory.FUEL, ExpenseCategory.MAINTENANCE, ExpenseCategory.REPAIR);

    private final Repos repos;
    private final AccessGuard access;
    private final Clock clock;

    public MonthlyFinanceService(Repos repos, AccessGuard access, Clock clock) {
        this.repos = repos;
        this.access = access;
        this.clock = clock;
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    public ZoneId zone() {
        return clock.getZone();
    }

    public YearMonth parsePeriod(Integer year, Integer month) {
        YearMonth current = currentMonth();
        if (year == null && month == null) {
            return current;
        }
        if (year == null || month == null) {
            throw new ApiException(400, "Both year and month are required.");
        }
        if (month < 1 || month > 12) {
            throw new ApiException(400, "Month must be between 1 and 12.");
        }
        if (year < 2000 || year > current.getYear() + 1) {
            throw new ApiException(400, "Year is out of range.");
        }
        return YearMonth.of(year, month);
    }

    public Map<String, Object> authorizedSummary(Integer year, Integer month, Long branchId) {
        requireFinanceRead();
        Long scopedBranch = access.scopedBranchId(branchId);
        Long organizationId = access.organizationScopeId();
        YearMonth period = parsePeriod(year, month);
        return summaryWithComparison(period, organizationId, scopedBranch);
    }

    public Map<String, Object> authorizedHistory(int months, Long branchId) {
        requireFinanceRead();
        Long scopedBranch = access.scopedBranchId(branchId);
        Long organizationId = access.organizationScopeId();
        int limit = Math.min(Math.max(months, 1), 36);
        List<Map<String, Object>> rows = new ArrayList<>();
        YearMonth cursor = currentMonth();
        for (int i = 0; i < limit; i++) {
            rows.add(summaryWithComparison(cursor, organizationId, scopedBranch));
            cursor = cursor.minusMonths(1);
        }
        return Map.of("timezone", zone().getId(), "months", rows);
    }

    public Map<String, Object> authorizedDetails(Integer year, Integer month, Long branchId, Long vehicleId,
                                                 String tripStatus, String expenseCategory, String expenseStatus,
                                                 int page, int size) {
        requireFinanceRead();
        Long scopedBranch = access.scopedBranchId(branchId);
        Long organizationId = access.organizationScopeId();
        YearMonth period = parsePeriod(year, month);
        PeriodBounds bounds = bounds(period);
        TripStatus status = tripStatus == null || tripStatus.isBlank() ? null : TripStatus.valueOf(tripStatus);
        ExpenseCategory category = expenseCategory == null || expenseCategory.isBlank()
                ? null : ExpenseCategory.valueOf(expenseCategory);
        ExpenseStatus expStatus = expenseStatus == null || expenseStatus.isBlank()
                ? null : ExpenseStatus.valueOf(expenseStatus);
        var tripPage = repos.trips.searchPeriod(bounds.startDateTime, bounds.endDateTime, scopedBranch, organizationId,
                status, vehicleId, PageRequest.of(page, size, Sort.by("id").descending()));
        var expensePage = repos.expenses.searchPeriod(bounds.startDate, bounds.endDate, scopedBranch, organizationId,
                expStatus, category, vehicleId, PageRequest.of(page, size, Sort.by("id").descending()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period.toString());
        result.put("summary", summarize(period, organizationId, scopedBranch));
        result.put("trips", pageOf(tripPage, this::tripRow));
        result.put("expenses", pageOf(expensePage, this::expenseRow));
        return result;
    }

    public Map<String, Object> summaryWithComparison(YearMonth period, Long organizationId, Long branchId) {
        Map<String, Object> current = summarize(period, organizationId, branchId);
        Map<String, Object> previous = summarize(period.minusMonths(1), organizationId, branchId);
        current.put("previousPeriod", previous.get("period"));
        current.put("comparison", comparison(current, previous));
        return current;
    }

    public Map<String, Object> summarize(YearMonth period, Long organizationId, Long branchId) {
        PeriodBounds bounds = bounds(period);
        YearMonth now = currentMonth();
        BigDecimal revenue = Money.of(repos.trips.sumRevenueByStatusInPeriod(TripStatus.COMPLETED,
                bounds.startDateTime, bounds.endDateTime, branchId, organizationId));
        long completed = repos.trips.countByStatusCompletedInPeriod(TripStatus.COMPLETED,
                bounds.startDateTime, bounds.endDateTime, branchId, organizationId);
        long cancelled = repos.trips.countByStatusCompletedInPeriod(TripStatus.CANCELLED,
                bounds.startDateTime, bounds.endDateTime, branchId, organizationId);
        long missingRevenue = repos.trips.countMissingRevenue(TripStatus.COMPLETED,
                bounds.startDateTime, bounds.endDateTime, branchId, organizationId);
        BigDecimal distance = Money.of(repos.trips.sumDistanceByStatusInPeriod(TripStatus.COMPLETED,
                bounds.startDateTime, bounds.endDateTime, branchId, organizationId));
        long created = repos.trips.countCreatedInPeriod(bounds.startInstant, bounds.endInstant, branchId, organizationId);
        long delayed = repos.trips.countDelayedInPeriod(TripStatus.DELAYED, bounds.startInstant, bounds.endInstant,
                branchId, organizationId);

        BigDecimal approvedTotal = Money.of(repos.expenses.sumByStatusAndPeriod(ExpenseStatus.APPROVED,
                bounds.startDate, bounds.endDate, branchId, organizationId, null));
        BigDecimal fuel = Money.of(repos.expenses.sumByStatusPeriodAndCategories(ExpenseStatus.APPROVED,
                bounds.startDate, bounds.endDate, FUEL_CATEGORIES, branchId, organizationId));
        BigDecimal maintenance = Money.of(repos.expenses.sumByStatusPeriodAndCategories(ExpenseStatus.APPROVED,
                bounds.startDate, bounds.endDate, MAINTENANCE_CATEGORIES, branchId, organizationId));
        BigDecimal other = Money.of(repos.expenses.sumByStatusPeriodExcluding(ExpenseStatus.APPROVED,
                bounds.startDate, bounds.endDate, LEDGER_EXCLUSIONS_FOR_OTHER, branchId, organizationId));
        long pendingAll = repos.expenses.countByStatusScoped(ExpenseStatus.PENDING, branchId, organizationId);
        long pendingInMonth = repos.expenses.countByStatusAndPeriod(ExpenseStatus.PENDING,
                bounds.startDate, bounds.endDate, branchId, organizationId);

        boolean revenueComplete = missingRevenue == 0;
        boolean profitAvailable = revenueComplete;
        BigDecimal profit = profitAvailable ? revenue.subtract(approvedTotal) : null;
        BigDecimal costPerKm = distance.compareTo(BigDecimal.ZERO) > 0
                ? approvedTotal.divide(distance, 4, RoundingMode.HALF_UP) : null;

        long vehicles = repos.vehicles.search(branchId, organizationId, null, null, null, PageRequest.of(0, 1)).getTotalElements();
        long onTrip = repos.vehicles.search(branchId, organizationId, null, VehicleStatus.ON_TRIP, null, PageRequest.of(0, 1))
                .getTotalElements();
        long assigned = repos.vehicles.search(branchId, organizationId, null, VehicleStatus.ASSIGNED, null, PageRequest.of(0, 1))
                .getTotalElements();
        Double utilization = vehicles == 0 ? null : ((onTrip + assigned) * 100.0 / vehicles);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", period.toString());
        data.put("year", period.getYear());
        data.put("month", period.getMonthValue());
        data.put("monthLabel", period.getMonth().name() + " " + period.getYear());
        data.put("timezone", zone().getId());
        data.put("startInclusive", bounds.startDate.toString());
        data.put("endExclusive", bounds.endDate.toString());
        data.put("currentMonth", period.equals(now));
        data.put("futurePeriod", period.isAfter(now));
        data.put("empty", completed == 0 && approvedTotal.compareTo(BigDecimal.ZERO) == 0
                && pendingInMonth == 0 && created == 0);
        data.put("totalRevenue", revenue);
        data.put("revenueComplete", revenueComplete);
        data.put("completedTripsMissingRevenue", missingRevenue);
        data.put("totalExpenses", approvedTotal);
        data.put("fuelExpenses", fuel);
        data.put("maintenanceExpenses", maintenance);
        data.put("otherExpenses", other);
        data.put("expenseDefinition", "Approved expenses in the expense ledger for the selected month. "
                + "Fuel and maintenance posted through operations already create ledger rows, so those source tables are not added again.");
        data.put("pendingExpenseApprovals", pendingAll);
        data.put("pendingExpenseApprovalsDefinition", "All currently pending expenses, regardless of month.");
        data.put("pendingExpenseApprovalsInMonth", pendingInMonth);
        data.put("netProfit", profit);
        data.put("profitAvailable", profitAvailable);
        data.put("profitUnavailableReason", profitAvailable ? null
                : "Profit is unavailable until every completed trip in the month has a recorded revenue amount.");
        data.put("completedTrips", completed);
        data.put("createdTrips", created);
        data.put("cancelledTrips", cancelled);
        data.put("delayedTrips", delayed);
        data.put("totalDistanceKm", distance);
        data.put("costPerKm", costPerKm);
        data.put("costPerKmDefinition", "Approved operating expenses divided by completed-trip distance for the same month.");
        data.put("utilizationPercent", utilization);
        data.put("utilizationDefinition", "Live share of vehicles currently assigned or on a trip, not a historical month average.");
        return data;
    }

    public List<Map<String, Object>> branchBreakdown(YearMonth period, Long organizationId) {
        List<Branch> branches = organizationId == null
                ? repos.branches.findAll()
                : repos.branches.findByOrganization_Id(organizationId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Branch branch : branches) {
            Map<String, Object> summary = summarize(period, organizationId, branch.getId());
            summary.put("branchId", branch.getId());
            summary.put("branchName", branch.getName());
            summary.put("organizationName", branch.getOrganization() == null ? null : branch.getOrganization().getName());
            rows.add(summary);
        }
        return rows;
    }

    public String exportCsv(Integer year, Integer month, Long branchId, Long vehicleId, String tripStatus,
                            String expenseCategory, String expenseStatus) {
        requireFinanceRead();
        Map<String, Object> details = authorizedDetails(year, month, branchId, vehicleId, tripStatus,
                expenseCategory, expenseStatus, 0, 2000);
        StringBuilder csv = new StringBuilder();
        csv.append("section,id,date,status,category,amount,distanceKm,revenue,branch,vehicle\n");
        @SuppressWarnings("unchecked")
        PageResponse<Map<String, Object>> trips = (PageResponse<Map<String, Object>>) details.get("trips");
        for (Map<String, Object> trip : trips.content()) {
            csv.append("trip,").append(trip.get("id")).append(',')
                    .append(trip.get("reportingDate")).append(',')
                    .append(trip.get("status")).append(',')
                    .append("REVENUE,").append(trip.get("revenueAmount")).append(',')
                    .append(trip.get("distanceKm")).append(',')
                    .append(trip.get("revenueAmount")).append(',')
                    .append(csv(trip.get("branchName"))).append(',')
                    .append(csv(trip.get("vehicleReg"))).append('\n');
        }
        @SuppressWarnings("unchecked")
        PageResponse<Map<String, Object>> expenses = (PageResponse<Map<String, Object>>) details.get("expenses");
        for (Map<String, Object> expense : expenses.content()) {
            csv.append("expense,").append(expense.get("id")).append(',')
                    .append(expense.get("date")).append(',')
                    .append(expense.get("status")).append(',')
                    .append(expense.get("category")).append(',')
                    .append(expense.get("amount")).append(",,")
                    .append(',')
                    .append(csv(expense.get("branchName"))).append(',')
                    .append(csv(expense.get("vehicleReg"))).append('\n');
        }
        return csv.toString();
    }

    public PeriodBounds bounds(YearMonth period) {
        ZoneId zone = zone();
        LocalDate start = period.atDay(1);
        LocalDate end = period.plusMonths(1).atDay(1);
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atStartOfDay();
        Instant startInstant = start.atStartOfDay(zone).toInstant();
        Instant endInstant = end.atStartOfDay(zone).toInstant();
        return new PeriodBounds(start, end, startDateTime, endDateTime, startInstant, endInstant);
    }

    @Transactional
    public Map<String, Object> recordTripRevenue(Long tripId, Object amountValue) {
        access.require(Role.FINANCE_OFFICER, Role.SUPER_ADMIN);
        Trip trip = repos.trips.findById(tripId).orElseThrow(() -> new ApiException(404, "Trip not found."));
        access.assertBranch(trip.getBranch());
        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new ApiException(400, "Revenue can only be recorded for completed trips.");
        }
        if (amountValue == null || String.valueOf(amountValue).isBlank()) {
            throw new ApiException(400, "Revenue amount is required.");
        }
        BigDecimal amount = Money.of(new BigDecimal(String.valueOf(amountValue)));
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(400, "Revenue cannot be negative.");
        }
        trip.setRevenueAmount(amount);
        repos.trips.save(trip);
        access.audit(repos, "Recorded trip revenue", "Trip", trip.getId());
        return tripRow(trip);
    }

    private void requireFinanceRead() {
        access.require(Role.SUPER_ADMIN, Role.FINANCE_OFFICER);
    }

    private Map<String, Object> comparison(Map<String, Object> current, Map<String, Object> previous) {
        Map<String, Object> row = new LinkedHashMap<>();
        putDelta(row, "revenue", (BigDecimal) current.get("totalRevenue"), (BigDecimal) previous.get("totalRevenue"));
        putDelta(row, "expenses", (BigDecimal) current.get("totalExpenses"), (BigDecimal) previous.get("totalExpenses"));
        boolean bothProfit = Boolean.TRUE.equals(current.get("profitAvailable"))
                && Boolean.TRUE.equals(previous.get("profitAvailable"));
        if (bothProfit) {
            putDelta(row, "profit", (BigDecimal) current.get("netProfit"), (BigDecimal) previous.get("netProfit"));
        } else {
            row.put("profitChange", null);
            row.put("profitChangePercent", null);
            row.put("profitChangeNote", "Profit comparison requires complete revenue in both months.");
        }
        long currentTrips = ((Number) current.get("completedTrips")).longValue();
        long previousTrips = ((Number) previous.get("completedTrips")).longValue();
        row.put("completedTripsChange", currentTrips - previousTrips);
        row.put("completedTripsChangePercent", Money.percentChange(currentTrips, previousTrips));
        row.put("expenseIncreaseIsNotPositive", true);
        return row;
    }

    private void putDelta(Map<String, Object> row, String name, BigDecimal current, BigDecimal previous) {
        BigDecimal change = current.subtract(previous);
        row.put(name + "Change", change);
        row.put(name + "ChangePercent", Money.percentChange(current, previous));
    }

    private Map<String, Object> tripRow(Trip trip) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", trip.getId());
        map.put("status", trip.getStatus().name());
        map.put("startDate", trip.getStartDate());
        map.put("actualEndDate", trip.getActualEndDate());
        map.put("reportingDate", trip.getActualEndDate() != null ? trip.getActualEndDate() : trip.getStartDate());
        map.put("distanceKm", trip.getDistanceKm());
        map.put("revenueAmount", trip.getRevenueAmount());
        map.put("branchName", trip.getBranch() == null ? null : trip.getBranch().getName());
        map.put("vehicleReg", trip.getVehicle() == null ? null : trip.getVehicle().getRegistrationNumber());
        map.put("routeLabel", trip.getRoute() == null ? null : trip.getRoute().getSource() + " → " + trip.getRoute().getDestination());
        return map;
    }

    private Map<String, Object> expenseRow(Expense expense) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", expense.getId());
        map.put("category", expense.getCategory());
        map.put("amount", expense.getAmount());
        map.put("date", expense.getDate());
        map.put("status", expense.getStatus());
        map.put("description", expense.getDescription());
        map.put("branchName", expense.getBranch() == null ? null : expense.getBranch().getName());
        map.put("vehicleReg", expense.getVehicle() == null ? null : expense.getVehicle().getRegistrationNumber());
        return map;
    }

    private <T> PageResponse<Map<String, Object>> pageOf(org.springframework.data.domain.Page<T> page,
                                                         java.util.function.Function<T, Map<String, Object>> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return text.contains(",") ? "\"" + text + "\"" : text;
    }

    public record PeriodBounds(LocalDate startDate, LocalDate endDate, LocalDateTime startDateTime,
                               LocalDateTime endDateTime, Instant startInstant, Instant endInstant) {
    }
}

package com.example.demo.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.demo.domain.AppNotification;
import com.example.demo.domain.AppUser;
import com.example.demo.domain.Branch;
import com.example.demo.domain.Codes.DriverStatus;
import com.example.demo.domain.Codes.ExpenseCategory;
import com.example.demo.domain.Codes.ExpenseStatus;
import com.example.demo.domain.Codes.IncidentSeverity;
import com.example.demo.domain.Codes.MaintenanceStatus;
import com.example.demo.domain.Codes.MaintenanceType;
import com.example.demo.domain.Codes.Role;
import com.example.demo.domain.Codes.TripStatus;
import com.example.demo.domain.Codes.VehicleStatus;
import com.example.demo.domain.Driver;
import com.example.demo.domain.Expense;
import com.example.demo.domain.FuelRecord;
import com.example.demo.domain.Incident;
import com.example.demo.domain.MaintenanceRecord;
import com.example.demo.domain.Organization;
import com.example.demo.domain.Route;
import com.example.demo.domain.Trip;
import com.example.demo.domain.TripStatusHistory;
import com.example.demo.domain.Vehicle;
import com.example.demo.repo.Repos;

@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {
    private final Repos repos;
    private final PasswordEncoder encoder;

    public DataSeeder(Repos repos, PasswordEncoder encoder) {
        this.repos = repos;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (repos.users.count() > 0) {
            return;
        }
        Organization org = new Organization();
        org.setName("DriveOps Logistics");
        org.setContactEmail("ops@driveops.local");
        org.setContactPhone("040-40004000");
        org.setAddress("Hyderabad, Telangana");
        repos.organizations.save(org);

        Branch hyd = branch(org, "Hyderabad Hub", "Hyderabad", "HITEC City, Hyderabad", "040-11112222");
        Branch blr = branch(org, "Bengaluru Hub", "Bengaluru", "Whitefield, Bengaluru", "080-33334444");
        repos.branches.save(hyd);
        repos.branches.save(blr);

        AppUser superAdmin = user("superadmin", "superadmin@driveops.local", "SuperAdmin@123", "Asha Rao", Role.SUPER_ADMIN, org, null);
        AppUser fleetMgr = user("fleetmgr", "fleet@driveops.local", "FleetMgr@123", "Arjun Mehta", Role.FLEET_MANAGER, org, null);
        AppUser hydMgr = user("branchmgr", "hyd.mgr@driveops.local", "BranchMgr@123", "Maya Singh", Role.BRANCH_MANAGER, org, hyd);
        user("blrmgr", "blr.mgr@driveops.local", "BranchMgr@123", "Kabir Nair", Role.BRANCH_MANAGER, org, blr);
        AppUser finance = user("finance", "finance@driveops.local", "Finance@123", "Neha Kapoor", Role.FINANCE_OFFICER, org, null);
        AppUser driverUser = user("driver", "ravi@driveops.local", "Driver@123", "Ravi Kumar", Role.DRIVER, org, hyd);
        AppUser driverUserB = user("driver2", "anita@driveops.local", "Driver@123", "Anita Das", Role.DRIVER, org, blr);

        hyd.setManagerUserId(hydMgr.getId());
        repos.branches.save(hyd);

        Vehicle v1 = vehicle("TS09XX1234", "Truck", "Tata", "Prima", hyd, VehicleStatus.AVAILABLE, LocalDate.of(2026, 10, 15));
        Vehicle v2 = vehicle("TS09XX5678", "Truck", "BharatBenz", "3123", hyd, VehicleStatus.ASSIGNED, LocalDate.of(2027, 1, 20));
        Vehicle v3 = vehicle("KA03YY2222", "Van", "Ashok Leyland", "Dost", blr, VehicleStatus.MAINTENANCE, LocalDate.of(2026, 11, 2));
        Vehicle v4 = vehicle("KA03YY8888", "Truck", "Volvo", "FH16", blr, VehicleStatus.AVAILABLE, LocalDate.of(2027, 4, 12));

        Driver d1 = driver("Ravi Kumar", "HYD-LIC-001", hyd, driverUser.getId(), DriverStatus.ASSIGNED);
        Driver d2 = driver("Sana Iyer", "HYD-LIC-002", hyd, null, DriverStatus.AVAILABLE);
        Driver d3 = driver("Anita Das", "BLR-LIC-001", blr, driverUserB.getId(), DriverStatus.AVAILABLE);
        d1.setAssignedVehicleId(v2.getId());
        v2.setAssignedDriverId(d1.getId());
        repos.drivers.save(d1);
        repos.vehicles.save(v2);

        Route r1 = route(hyd, "Hyderabad", "Vijayawada", 270);
        Route r2 = route(hyd, "Hyderabad", "Warangal", 150);
        Route r3 = route(blr, "Bengaluru", "Mysuru", 145);

        Trip t1 = trip(v2, d1, r1, hyd, TripStatus.ASSIGNED, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
        history(t1, null, TripStatus.PLANNED, "superadmin");
        history(t1, TripStatus.PLANNED, TripStatus.ASSIGNED, "branchmgr");
        trip(null, null, r2, hyd, TripStatus.PLANNED, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(4));

        MaintenanceRecord m = new MaintenanceRecord();
        m.setVehicle(v3);
        m.setServiceType(MaintenanceType.BRAKE_SERVICE);
        m.setServiceDate(LocalDate.now().minusDays(1));
        m.setCurrentMileage(82000);
        m.setServiceCost(18000.0);
        m.setNextServiceDate(LocalDate.now().plusDays(20));
        m.setNextServiceMileage(86000);
        m.setDescription("Brake pads and rotor inspection");
        m.setStatus(MaintenanceStatus.IN_PROGRESS);
        repos.maintenance.save(m);

        FuelRecord fuel = new FuelRecord();
        fuel.setVehicle(v2);
        fuel.setDriver(d1);
        fuel.setDate(LocalDate.now().minusDays(2));
        fuel.setOdometer(54120);
        fuel.setFuelType("Diesel");
        fuel.setQuantity(80.0);
        fuel.setPricePerLiter(98.5);
        fuel.setTotalCost(80.0 * 98.5);
        fuel.setFuelStation("HP HITEC");
        repos.fuel.save(fuel);

        Expense e1 = expense(v2, d1, hyd, t1, ExpenseCategory.FUEL, fuel.getTotalCost(), "Fuel fill for Vijayawada trip");
        Expense e2 = expense(v3, d3, blr, null, ExpenseCategory.MAINTENANCE, 18000.0, "Brake service");
        e2.setStatus(ExpenseStatus.PENDING);
        repos.expenses.save(e2);

        Incident incident = new Incident();
        incident.setVehicle(v1);
        incident.setDriver(d2);
        incident.setBranch(hyd);
        incident.setOccurredAt(LocalDateTime.now().minusHours(6));
        incident.setLocation("Outer Ring Road");
        incident.setSeverity(IncidentSeverity.MEDIUM);
        incident.setDescription("Minor bumper scrape in depot parking");
        repos.incidents.save(incident);

        notify(superAdmin.getId(), null, "Welcome to DriveOps", "Demo data is ready. Explore each role dashboard.");
        notify(hydMgr.getId(), hyd.getId(), "Branch assigned", "You manage Hyderabad Hub only.");
        notify(driverUser.getId(), hyd.getId(), "New trip assigned to you.", "Trip #" + t1.getId() + " is assigned.");
        notify(finance.getId(), null, "Expense submitted for approval.", "Fuel and maintenance expenses are waiting.");
        notify(fleetMgr.getId(), null, "Vehicle insurance expires in 14 days.", v1.getRegistrationNumber() + " insurance expires 15 Oct 2026.");
    }

    private Branch branch(Organization org, String name, String location, String address, String phone) {
        Branch branch = new Branch();
        branch.setOrganization(org);
        branch.setName(name);
        branch.setLocation(location);
        branch.setAddress(address);
        branch.setContactNumber(phone);
        return branch;
    }

    private AppUser user(String username, String email, String password, String name, Role role, Organization org, Branch branch) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encoder.encode(password));
        user.setFullName(name);
        user.setRole(role);
        user.setOrganization(org);
        user.setBranch(branch);
        return repos.users.save(user);
    }

    private Vehicle vehicle(String reg, String type, String maker, String model, Branch branch, VehicleStatus status, LocalDate insurance) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber(reg);
        vehicle.setVehicleType(type);
        vehicle.setManufacturer(maker);
        vehicle.setModel(model);
        vehicle.setYear(2022);
        vehicle.setFuelType("Diesel");
        vehicle.setCapacity(18);
        vehicle.setCurrentMileage(54000);
        vehicle.setBranch(branch);
        vehicle.setStatus(status);
        vehicle.setInsuranceExpiry(insurance);
        vehicle.setRegistrationExpiry(LocalDate.of(2027, 6, 1));
        vehicle.setPollutionExpiry(LocalDate.of(2026, 12, 1));
        return repos.vehicles.save(vehicle);
    }

    private Driver driver(String name, String license, Branch branch, Long userId, DriverStatus status) {
        Driver driver = new Driver();
        driver.setName(name);
        driver.setPhone("9876543210");
        driver.setEmail(name.toLowerCase().replace(" ", ".") + "@driveops.local");
        driver.setAddress(branch.getAddress());
        driver.setLicenseNumber(license);
        driver.setLicenseType("HMV");
        driver.setLicenseExpiry(LocalDate.of(2027, 3, 15));
        driver.setJoiningDate(LocalDate.of(2024, 1, 10));
        driver.setBranch(branch);
        driver.setUserId(userId);
        driver.setStatus(status);
        return repos.drivers.save(driver);
    }

    private Route route(Branch branch, String src, String dest, double km) {
        Route route = new Route();
        route.setBranch(branch);
        route.setSource(src);
        route.setDestination(dest);
        route.setDistanceKm(km);
        route.setEstimatedDurationMinutes((int) Math.round(km / 40.0 * 60));
        route.setDescription(src + " to " + dest);
        return repos.routes.save(route);
    }

    private Trip trip(Vehicle vehicle, Driver driver, Route route, Branch branch, TripStatus status, LocalDateTime start, LocalDateTime end) {
        Trip trip = new Trip();
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setRoute(route);
        trip.setBranch(branch);
        trip.setStartDate(start);
        trip.setExpectedEndDate(end);
        trip.setPurpose("Goods movement");
        trip.setDistanceKm(route.getDistanceKm());
        trip.setStatus(status);
        return repos.trips.save(trip);
    }

    private void history(Trip trip, TripStatus oldStatus, TripStatus next, String actor) {
        TripStatusHistory history = new TripStatusHistory();
        history.setTrip(trip);
        history.setOldStatus(oldStatus);
        history.setNewStatus(next);
        history.setChangedBy(actor);
        history.setRemarks("Seeded");
        repos.tripHistory.save(history);
    }

    private Expense expense(Vehicle vehicle, Driver driver, Branch branch, Trip trip, ExpenseCategory category, Double amount, String desc) {
        Expense expense = new Expense();
        expense.setVehicle(vehicle);
        expense.setDriver(driver);
        expense.setBranch(branch);
        expense.setTrip(trip);
        expense.setCategory(category);
        expense.setAmount(amount);
        expense.setDate(LocalDate.now().minusDays(1));
        expense.setDescription(desc);
        expense.setStatus(ExpenseStatus.PENDING);
        return repos.expenses.save(expense);
    }

    private void notify(Long userId, Long branchId, String title, String message) {
        AppNotification n = new AppNotification();
        n.setUserId(userId);
        n.setBranchId(branchId);
        n.setTitle(title);
        n.setMessage(message);
        n.setType("INFO");
        repos.notifications.save(n);
    }
}

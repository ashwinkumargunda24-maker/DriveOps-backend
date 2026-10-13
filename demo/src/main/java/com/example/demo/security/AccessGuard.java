package com.example.demo.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.example.demo.domain.AppUser;
import com.example.demo.domain.Branch;
import com.example.demo.domain.Codes.Role;
import com.example.demo.repo.BranchRepository;
import com.example.demo.web.ApiException;

@Component
public class AccessGuard {
    private final BranchRepository branches;

    public AccessGuard(BranchRepository branches) {
        this.branches = branches;
    }

    public UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ApiException(401, "Unauthorized. Please sign in.");
        }
        return principal;
    }

    public AppUser user() {
        return principal().getUser();
    }

    public boolean is(Role... roles) {
        Role current = user().getRole();
        for (Role role : roles) {
            if (current == role) {
                return true;
            }
        }
        return false;
    }

    public Long organizationScopeId() {
        AppUser user = user();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return null;
        }
        if (user.getOrganization() == null) {
            throw new ApiException(403, "Your account is not assigned to an organization.");
        }
        return user.getOrganization().getId();
    }

    public void require(Role... roles) {
        if (!is(roles)) {
            throw new ApiException(403, "You do not have permission for this action.");
        }
    }

    public Long scopedBranchId(Long requestedBranchId) {
        AppUser user = user();
        if (user.getRole() == Role.BRANCH_MANAGER || user.getRole() == Role.DRIVER) {
            if (user.getBranch() == null) {
                throw new ApiException(403, "Your account is not assigned to a branch.");
            }
            if (requestedBranchId != null && !requestedBranchId.equals(user.getBranch().getId())) {
                throw new ApiException(403, "You cannot access another branch.");
            }
            return user.getBranch().getId();
        }
        if (requestedBranchId != null) {
            Branch branch = branches.findById(requestedBranchId)
                    .orElseThrow(() -> new ApiException(404, "Branch not found."));
            assertBranch(branch);
        }
        return requestedBranchId;
    }

    public void assertBranch(Branch branch) {
        if (branch == null) {
            throw new ApiException(400, "Branch is required.");
        }
        AppUser user = user();
        if (user.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (user.getOrganization() == null || branch.getOrganization() == null
                || !user.getOrganization().getId().equals(branch.getOrganization().getId())) {
            throw new ApiException(403, "You cannot access another organization.");
        }
        if ((user.getRole() == Role.BRANCH_MANAGER || user.getRole() == Role.DRIVER)
                && (user.getBranch() == null || !user.getBranch().getId().equals(branch.getId()))) {
            throw new ApiException(403, "You cannot access another branch.");
        }
    }

    public void audit(com.example.demo.repo.Repos repos, String action, String entityType, Object entityId) {
        com.example.demo.domain.AuditLog log = new com.example.demo.domain.AuditLog();
        log.setActor(user().getUsername());
        log.setRole(user().getRole().name());
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId == null ? null : String.valueOf(entityId));
        repos.audits.save(log);
    }
}

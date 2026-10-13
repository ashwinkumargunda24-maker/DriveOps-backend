package com.example.demo.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.demo.domain.AppUser;
import com.example.demo.domain.Codes.Role;

public class UserPrincipal implements UserDetails {
    private final AppUser user;

    public UserPrincipal(AppUser user) {
        this.user = user;
    }

    public AppUser getUser() {
        return user;
    }

    public Role getRole() {
        return user.getRole();
    }

    public Long getBranchId() {
        return user.getBranch() == null ? null : user.getBranch().getId();
    }

    public Long getOrgId() {
        return user.getOrganization() == null ? null : user.getOrganization().getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}

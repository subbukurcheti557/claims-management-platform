package com.icmp.identity.user.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.icmp.identity.common.error.ApiException;
import com.icmp.identity.user.domain.Role;
import com.icmp.identity.user.infrastructure.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/users")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','CLAIMS_MANAGER')")
    @Operation(summary = "List active staff users (officers and managers), optionally filtered by role")
    public List<UserResponse> listStaff(@RequestParam(required = false) Role role) {
        Set<Role> roles = role == null ? EnumSet.of(Role.CLAIMS_OFFICER, Role.CLAIMS_MANAGER) : EnumSet.of(role);
        if (roles.contains(Role.CLAIMANT)) {
            throw ApiException.forbidden("Claimant accounts cannot be listed.");
        }
        return users.findByRoleInAndActiveTrueOrderByFullName(roles).stream().map(UserResponse::from).toList();
    }

    /** Service-to-service lookup (blocked at the gateway). Used by claims-service to validate assignees. */
    @GetMapping("/internal/users/{id}")
    @Operation(summary = "Internal: look up a user by id")
    public UserResponse getInternal(@PathVariable UUID id) {
        return users.findById(id).filter(u -> u.isActive()).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User " + id + " not found"));
    }
}

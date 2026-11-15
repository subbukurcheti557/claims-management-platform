package com.icmp.identity.user.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.icmp.identity.user.domain.AppUser;
import com.icmp.identity.user.domain.Role;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    List<AppUser> findByRoleInAndActiveTrueOrderByFullName(Collection<Role> roles);
}

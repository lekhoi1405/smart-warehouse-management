package com.lekhoi.smart_warehouse.service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.lekhoi.smart_warehouse.domain.entity.Permission;
import com.lekhoi.smart_warehouse.domain.entity.Role;
import com.lekhoi.smart_warehouse.domain.entity.UserAccount;
import com.lekhoi.smart_warehouse.domain.entity.UserPermissionOverride;
import com.lekhoi.smart_warehouse.repository.UserAccountRepository;
import com.lekhoi.smart_warehouse.util.constant.AccountStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.RoleStatusEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthorityService {

    private final UserAccountRepository accountRepository;

    public record Snapshot(
        Long id,
        String username,
        String fullName,
        String password,
        AccountStatusEnum status,
        Set<String> roleNames,
        Set<String> permissionNames
    ) {
        public Set<GrantedAuthority> authorities() {
            Set<GrantedAuthority> result = new HashSet<>();

            roleNames.forEach(name -> result.add(new SimpleGrantedAuthority("ROLE_" + name)));
            permissionNames.forEach(name -> result.add(new SimpleGrantedAuthority(name)));

            return result;
        }
    }

    @Transactional(readOnly = true)
    public Snapshot load(String username) {
        UserAccount account = accountRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found"));

        return resolve(account);
    }

    @Transactional(readOnly = true)
    public Snapshot loadActive(String username) {
        Snapshot snapshot = load(username);

        if (snapshot.status() != AccountStatusEnum.ACTIVE) {
            throw new DisabledException("Account is disabled");
        }

        return snapshot;
    }

    public Snapshot resolve(UserAccount account) {
        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();

        account.getUserRoles().forEach(userRole -> {
            Role role = userRole.getRole();

            if (role.getStatus() == RoleStatusEnum.ACTIVE) {
                roles.add(role.getRoleName());
                permissions.addAll(role.getRolePermissions().stream()
                    .map(rolePermission -> rolePermission.getPermission().getPermissionName())
                    .collect(Collectors.toSet()));
            }
        });

        for (UserPermissionOverride override : account.getPermissionOverrides()) {
            String permissionName = override.getPermission().getPermissionName();

            if (override.isGranted()) {
                permissions.add(permissionName);
            } else {
                permissions.remove(permissionName);
            }
        }

        return new Snapshot(
            account.getId(),
            account.getUsername(),
            account.getFullName(),
            account.getPassword(),
            account.getStatus(),
            Set.copyOf(roles),
            Set.copyOf(permissions)
        );
    }
}

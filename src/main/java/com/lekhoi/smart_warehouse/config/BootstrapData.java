package com.lekhoi.smart_warehouse.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.lekhoi.smart_warehouse.domain.entity.Permission;
import com.lekhoi.smart_warehouse.domain.entity.Role;
import com.lekhoi.smart_warehouse.domain.entity.RolePermission;
import com.lekhoi.smart_warehouse.domain.entity.UserAccount;
import com.lekhoi.smart_warehouse.domain.entity.UserRole;
import com.lekhoi.smart_warehouse.repository.PermissionRepository;
import com.lekhoi.smart_warehouse.repository.RoleRepository;
import com.lekhoi.smart_warehouse.repository.UserAccountRepository;
import com.lekhoi.smart_warehouse.util.constant.AccountStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.RoleStatusEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BootstrapData implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${BOOTSTRAP_ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
    private String adminPassword;

    private static final List<String> PERMISSIONS = List.of(
        "ACCOUNT_ADMIN",
        "PRODUCT_MANAGE",
        "SUPPLIER_MANAGE",
        "STOCK_DOCUMENT_WRITE",
        "INVENTORY_VIEW",
        "STOCKTAKE_WRITE",
        "ALERT_VIEW",
        "ALERT_MANAGE",
        "STATISTICS_VIEW",
        "REPORT_EXPORT"
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String name : PERMISSIONS) {
            if (permissionRepository.findByPermissionName(name).isEmpty()) {
                Permission permission = new Permission();
                permission.setPermissionName(name);
                permission.setDescription(name.replace('_', ' '));
                permissionRepository.save(permission);
            }
        }

        createRoleIfMissing("ADMIN", Set.of("ACCOUNT_ADMIN"));

        createRoleIfMissing("WAREHOUSE_STAFF", Set.of(
            "STOCK_DOCUMENT_WRITE", "INVENTORY_VIEW", "STOCKTAKE_WRITE"
        ));

        createRoleIfMissing("WAREHOUSE_MANAGER", Set.of(
            "PRODUCT_MANAGE", "SUPPLIER_MANAGE", "STOCK_DOCUMENT_WRITE",
            "INVENTORY_VIEW", "STOCKTAKE_WRITE", "ALERT_VIEW",
            "ALERT_MANAGE", "STATISTICS_VIEW", "REPORT_EXPORT"
        ));

        createFirstAdmin();
    }

    private void createRoleIfMissing(String name, Set<String> permissionNames) {
        if (roleRepository.findByRoleName(name).isPresent()) return;

        Role role = new Role();
        role.setRoleName(name);
        role.setDescription(name.replace('_', ' '));
        role.setStatus(RoleStatusEnum.ACTIVE);

        Set<Permission> permissions = permissionNames.stream()
            .map(permissionName -> permissionRepository.findByPermissionName(permissionName)
                .orElseThrow(() -> new IllegalStateException("Missing permission: " + permissionName)))
            .collect(Collectors.toSet());

        for (Permission permission : permissions) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRole(role);
            rolePermission.setPermission(permission);
            role.getRolePermissions().add(rolePermission);
        }

        roleRepository.save(role);
    }

    private void createFirstAdmin() {
        if (adminUsername.isBlank() && adminPassword.isBlank()) return;

        if (adminUsername.isBlank() || adminPassword.length() < 12) {
            throw new IllegalStateException("Bootstrap admin requires a username and a password of at least 12 characters");
        }

        String username = adminUsername.trim().toLowerCase(Locale.ROOT);

        if (accountRepository.existsByUsername(username)) return;

        Role adminRole = roleRepository.findByRoleName("ADMIN")
            .orElseThrow(() -> new IllegalStateException("ADMIN role not found"));

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(adminPassword));
        account.setFullName("System Administrator");
        account.setStatus(AccountStatusEnum.ACTIVE);

        UserRole userRole = new UserRole();
        userRole.setAccount(account);
        userRole.setRole(adminRole);
        account.getUserRoles().add(userRole);

        accountRepository.save(account);
    }
}

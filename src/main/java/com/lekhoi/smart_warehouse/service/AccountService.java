
package com.lekhoi.smart_warehouse.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.lekhoi.smart_warehouse.domain.dto.AccountDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination.Meta;
import com.lekhoi.smart_warehouse.domain.entity.Permission;
import com.lekhoi.smart_warehouse.domain.entity.Role;
import com.lekhoi.smart_warehouse.domain.entity.UserAccount;
import com.lekhoi.smart_warehouse.domain.entity.UserPermissionOverride;
import com.lekhoi.smart_warehouse.domain.entity.UserRole;
import com.lekhoi.smart_warehouse.repository.PermissionRepository;
import com.lekhoi.smart_warehouse.repository.RefreshTokenRepository;
import com.lekhoi.smart_warehouse.repository.RoleRepository;
import com.lekhoi.smart_warehouse.repository.UserAccountRepository;
import com.lekhoi.smart_warehouse.util.constant.AccountStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.RoleStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserAccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorityService authorityService;

    @Transactional
    public AccountDTO.Response handleCreateAccount(AccountDTO.CreateRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);

        if (accountRepository.existsByUsername(username)) {
            throw new AlreadyExistsException("Username already exists");
        }

        Set<Role> roles = resolveRoles(request.roleNames());

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setFullName(request.fullName().trim());
        account.setEmail(request.email());
        account.setPhone(request.phone());
        account.setStatus(AccountStatusEnum.ACTIVE);

        for (Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setAccount(account);
            userRole.setRole(role);
            account.getUserRoles().add(userRole);
        }

        accountRepository.save(account);
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountDTO.Response handleGetAccount(Long id) {
        return toResponse(getAccount(id));
    }

    @Transactional(readOnly = true)
    public ResultPagination<List<AccountDTO.Response>> handleGetAllAccounts(int current, int pageSize) {
        if (current < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        Pageable pageable = PageRequest.of(current - 1, pageSize, Sort.by("id").ascending());
        Page<UserAccount> page = accountRepository.findAll(pageable);
        List<AccountDTO.Response> responses = page.getContent().stream().map(this::toResponse).toList();

        Meta meta = Meta.builder().current(page.getNumber() + 1).pageSize(page.getSize()).pages(page.getTotalPages()).total(page.getTotalElements()).build();

        return ResultPagination.<List<AccountDTO.Response>>builder().result(responses).meta(meta).build();
    }

    @Transactional(readOnly = true)
    public List<AccountDTO.RoleResponse> handleGetAllRoles() {
        return roleRepository.findAll().stream()
            .map(role -> new AccountDTO.RoleResponse(
                role.getId(),
                role.getRoleName(),
                role.getRolePermissions().stream()
                    .map(rolePermission -> rolePermission.getPermission().getPermissionName())
                    .collect(Collectors.toSet())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountDTO.PermissionResponse> handleGetAllPermissions() {
        return permissionRepository.findAll().stream()
            .map(permission -> new AccountDTO.PermissionResponse(
                permission.getId(),
                permission.getPermissionName(),
                permission.getDescription()
            ))
            .toList();
    }

    @Transactional
    public AccountDTO.Response handleReplaceRoles(Long id, AccountDTO.UpdateRolesRequest request) {
        UserAccount account = getAccount(id);
        preventEditingOwnAccess(account);

        Set<Role> roles = resolveRoles(request.roleNames());
        Set<Long> newRoleIds = roles.stream().map(Role::getId).collect(Collectors.toSet());
        Set<Long> existingRoleIds = account.getUserRoles().stream().map(userRole -> userRole.getRole().getId()).collect(Collectors.toSet());

        account.getUserRoles().removeIf(userRole -> !newRoleIds.contains(userRole.getRole().getId()));

        for (Role role : roles) {
            if (!existingRoleIds.contains(role.getId())) {
                UserRole userRole = new UserRole();
                userRole.setAccount(account);
                userRole.setRole(role);
                account.getUserRoles().add(userRole);
            }
        }

        return toResponse(account);
    }

    @Transactional
    public AccountDTO.Response handleReplacePermissions(Long id, AccountDTO.UpdatePermissionsRequest request) {
        UserAccount account = getAccount(id);
        preventEditingOwnAccess(account);

        Set<String> desired = normalizeNames(request.permissionNames());
        Map<String, Permission> available = getPermissionMap();

        if (!available.keySet().containsAll(desired)) {
            throw new IllegalArgumentException("Unknown permission in request");
        }

        Set<String> inherited = account.getUserRoles().stream()
            .map(UserRole::getRole)
            .filter(role -> role.getStatus() == RoleStatusEnum.ACTIVE)
            .flatMap(role -> role.getRolePermissions().stream())
            .map(rolePermission -> rolePermission.getPermission().getPermissionName())
            .collect(Collectors.toSet());

        Map<String, UserPermissionOverride> existing = account.getPermissionOverrides().stream()
            .collect(Collectors.toMap(override -> override.getPermission().getPermissionName(), override -> override));

        for (Permission permission : available.values()) {
            String name = permission.getPermissionName();
            boolean wanted = desired.contains(name);
            boolean inheritedPermission = inherited.contains(name);
            UserPermissionOverride override = existing.get(name);

            if (wanted == inheritedPermission) {
                if (override != null) account.getPermissionOverrides().remove(override);
            } else if (override != null) {
                override.setGranted(wanted);
            } else {
                UserPermissionOverride newOverride = new UserPermissionOverride();
                newOverride.setAccount(account);
                newOverride.setPermission(permission);
                newOverride.setGranted(wanted);
                account.getPermissionOverrides().add(newOverride);
            }
        }

        return toResponse(account);
    }

    @Transactional
    public AccountDTO.Response handleRevokePermission(Long id, String permissionName) {
        UserAccount account = getAccount(id);
        preventEditingOwnAccess(account);

        String name = permissionName.trim().toUpperCase(Locale.ROOT);
        Permission permission = permissionRepository.findByPermissionName(name)
            .orElseThrow(() -> new IdInvalidException("Permission not found: " + name));

        if (!authorityService.resolve(account).permissionNames().contains(name)) {
            throw new IllegalStateException("Account does not currently have this permission");
        }

        UserPermissionOverride override = account.getPermissionOverrides().stream()
            .filter(item -> item.getPermission().getId().equals(permission.getId()))
            .findFirst().orElse(null);

        if (override == null) {
            override = new UserPermissionOverride();
            override.setAccount(account);
            override.setPermission(permission);
            account.getPermissionOverrides().add(override);
        }

        override.setGranted(false);
        return toResponse(account);
    }

    @Transactional
    public AccountDTO.Response handleUpdateStatus(Long id, AccountDTO.UpdateStatusRequest request) {
        UserAccount account = getAccount(id);
        preventEditingOwnAccess(account);

        account.setStatus(request.status());

        if (request.status() == AccountStatusEnum.DISABLED) {
            refreshTokenRepository.deleteByAccountId(account.getId());
        }

        return toResponse(account);
    }

    private Set<Role> resolveRoles(Set<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }

        Set<String> normalized = normalizeNames(names);
        List<Role> roles = roleRepository.findByRoleNameIn(normalized);

        if (roles.size() != normalized.size() || roles.stream().anyMatch(role -> role.getStatus() != RoleStatusEnum.ACTIVE)) {
            throw new IllegalArgumentException("Invalid or inactive role");
        }

        return new HashSet<>(roles);
    }

    private Set<String> normalizeNames(Set<String> names) {
        if (names == null) throw new IllegalArgumentException("Missing permission or role names");

        Set<String> normalized = new HashSet<>();

        for (String name : names) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Blank permission or role name");
            normalized.add(name.trim().toUpperCase(Locale.ROOT));
        }

        return normalized;
    }

    private Map<String, Permission> getPermissionMap() {
        Map<String, Permission> result = new HashMap<>();
        permissionRepository.findAll().forEach(permission -> result.put(permission.getPermissionName(), permission));
        return result;
    }

    private UserAccount getAccount(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new IdInvalidException("Account not found: " + id));
    }

    private void preventEditingOwnAccess(UserAccount account) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        if (account.getUsername().equals(currentUsername)) {
            throw new IllegalStateException("Admin cannot change their own access through this endpoint");
        }
    }

    private AccountDTO.Response toResponse(UserAccount account) {
        AuthorityService.Snapshot snapshot = authorityService.resolve(account);

        return new AccountDTO.Response(
            account.getId(),
            account.getUsername(),
            account.getFullName(),
            account.getEmail(),
            account.getPhone(),
            account.getStatus(),
            snapshot.roleNames(),
            snapshot.permissionNames()
        );
    }
}

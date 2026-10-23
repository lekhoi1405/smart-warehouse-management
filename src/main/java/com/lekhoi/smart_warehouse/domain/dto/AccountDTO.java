package com.lekhoi.smart_warehouse.domain.dto;

import java.util.Set;

import com.lekhoi.smart_warehouse.util.constant.AccountStatusEnum;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public interface AccountDTO {

    record CreateRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String fullName,
        @Email String email,
        String phone,
        @NotEmpty Set<@NotBlank String> roleNames
    ) {}

    record UpdateRolesRequest(
        @NotEmpty Set<@NotBlank String> roleNames
    ) {}

    record UpdatePermissionsRequest(
        @NotNull Set<@NotBlank String> permissionNames
    ) {}

    record UpdateStatusRequest(
        @NotNull AccountStatusEnum status
    ) {}

    record Response(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        AccountStatusEnum status,
        Set<String> roleNames,
        Set<String> permissionNames
    ) {}

    record RoleResponse(
        Long id,
        String roleName,
        Set<String> permissionNames
    ) {}

    record PermissionResponse(
        Long id,
        String permissionName,
        String description
    ) {}
}

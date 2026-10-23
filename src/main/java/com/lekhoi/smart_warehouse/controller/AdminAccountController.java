package com.lekhoi.smart_warehouse.controller;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.AccountDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.service.AccountService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_ADMIN')")
public class AdminAccountController {

    private final AccountService accountService;

    @ApiMessage("Get accounts")
    @GetMapping("/accounts")
    public ResponseEntity<ResultPagination<List<AccountDTO.Response>>> getAccounts(
        @RequestParam(name = "current", defaultValue = "1") int current,
        @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {

        return ResponseEntity.ok(accountService.handleGetAllAccounts(current, pageSize));
    }

    @ApiMessage("Get account")
    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountDTO.Response> getAccount(@PathVariable("id") Long id) {
        return ResponseEntity.ok(accountService.handleGetAccount(id));
    }

    @ApiMessage("Create account")
    @PostMapping("/accounts")
    public ResponseEntity<AccountDTO.Response> createAccount(@Valid @RequestBody AccountDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.handleCreateAccount(request));
    }

    @ApiMessage("Update account roles")
    @PutMapping("/accounts/{id}/roles")
    public ResponseEntity<AccountDTO.Response> updateRoles(@PathVariable("id") Long id, @Valid @RequestBody AccountDTO.UpdateRolesRequest request) {
        return ResponseEntity.ok(accountService.handleReplaceRoles(id, request));
    }

    @ApiMessage("Update account permissions")
    @PutMapping("/accounts/{id}/permissions")
    public ResponseEntity<AccountDTO.Response> updatePermissions(@PathVariable("id") Long id, @Valid @RequestBody AccountDTO.UpdatePermissionsRequest request) {
        return ResponseEntity.ok(accountService.handleReplacePermissions(id, request));
    }

    @ApiMessage("Revoke account permission")
    @DeleteMapping("/accounts/{id}/permissions/{permissionName}")
    public ResponseEntity<AccountDTO.Response> revokePermission(@PathVariable("id") Long id, @PathVariable("permissionName") String permissionName) {
        return ResponseEntity.ok(accountService.handleRevokePermission(id, permissionName));
    }

    @ApiMessage("Update account status")
    @PutMapping("/accounts/{id}/status")
    public ResponseEntity<AccountDTO.Response> updateStatus(@PathVariable("id") Long id, @Valid @RequestBody AccountDTO.UpdateStatusRequest request) {
        return ResponseEntity.ok(accountService.handleUpdateStatus(id, request));
    }

    @ApiMessage("Get roles")
    @GetMapping("/roles")
    public ResponseEntity<List<AccountDTO.RoleResponse>> getRoles() {
        return ResponseEntity.ok(accountService.handleGetAllRoles());
    }

    @ApiMessage("Get permissions")
    @GetMapping("/permissions")
    public ResponseEntity<List<AccountDTO.PermissionResponse>> getPermissions() {
        return ResponseEntity.ok(accountService.handleGetAllPermissions());
    }
}

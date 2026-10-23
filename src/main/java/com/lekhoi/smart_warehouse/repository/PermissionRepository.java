package com.lekhoi.smart_warehouse.repository;

import java.util.Optional;

import com.lekhoi.smart_warehouse.domain.entity.Permission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPermissionName(String permissionName);
}

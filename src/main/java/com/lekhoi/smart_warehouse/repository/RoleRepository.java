package com.lekhoi.smart_warehouse.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.lekhoi.smart_warehouse.domain.entity.Role;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleName(String roleName);

    List<Role> findByRoleNameIn(Set<String> roleNames);
}

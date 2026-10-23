package com.lekhoi.smart_warehouse.repository;

import java.util.Optional;

import com.lekhoi.smart_warehouse.domain.entity.UserAccount;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);
}

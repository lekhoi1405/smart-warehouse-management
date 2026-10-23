package com.lekhoi.smart_warehouse.repository;

import java.util.Optional;

import com.lekhoi.smart_warehouse.domain.entity.RefreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query(value = "SELECT r FROM RefreshToken r JOIN FETCH r.account "
            + "WHERE r.token = :token")
    Optional<RefreshToken> findByTokenWithAccount(@Param("token") String token);

    @Modifying
    @Query(value = "DELETE FROM RefreshToken r WHERE r.token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying
    @Query(value = "DELETE FROM RefreshToken r WHERE r.account.id = :accountId")
    void deleteByAccountId(@Param("accountId") Long accountId);
}

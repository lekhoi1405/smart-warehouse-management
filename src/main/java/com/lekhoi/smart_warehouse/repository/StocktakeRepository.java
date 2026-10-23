package com.lekhoi.smart_warehouse.repository;

import java.util.Optional;

import com.lekhoi.smart_warehouse.domain.entity.Stocktake;
import com.lekhoi.smart_warehouse.util.constant.StocktakeStatusEnum;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {

    boolean existsByStocktakeCode(String stocktakeCode);

    @Query(value = "SELECT DISTINCT s FROM Stocktake s "
            + "LEFT JOIN FETCH s.details d "
            + "LEFT JOIN FETCH d.product "
            + "WHERE s.id = :id")
    Optional<Stocktake> findWithDetailsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT s FROM Stocktake s WHERE s.id = :id")
    Optional<Stocktake> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT s FROM Stocktake s "
            + "WHERE (:status IS NULL OR s.status = :status)",
            countQuery = "SELECT COUNT(s) FROM Stocktake s "
                    + "WHERE (:status IS NULL OR s.status = :status)")
    Page<Stocktake> findFiltered(@Param("status") StocktakeStatusEnum status, Pageable pageable);
}

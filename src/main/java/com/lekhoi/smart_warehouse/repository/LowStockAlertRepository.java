package com.lekhoi.smart_warehouse.repository;

import java.util.List;
import java.util.Optional;

import com.lekhoi.smart_warehouse.domain.entity.LowStockAlert;
import com.lekhoi.smart_warehouse.util.constant.LowStockAlertStatusEnum;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT a FROM LowStockAlert a "
            + "WHERE a.product.id = :productId "
            + "AND a.status <> :resolved "
            + "ORDER BY a.id")
    List<LowStockAlert> findOpenByProductIdForUpdate(
        @Param("productId") Long productId,
        @Param("resolved") LowStockAlertStatusEnum resolved
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT a FROM LowStockAlert a WHERE a.id = :id")
    Optional<LowStockAlert> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT a FROM LowStockAlert a JOIN FETCH a.product WHERE a.id = :id")
    Optional<LowStockAlert> findWithProductById(@Param("id") Long id);

    @Query(value = "SELECT a FROM LowStockAlert a JOIN FETCH a.product p "
            + "WHERE (:status IS NULL OR a.status = :status) "
            + "AND (:includeResolved = true OR :status IS NOT NULL OR a.status <> :resolved) "
            + "AND a.archived = :archived "
            + "AND (:productCode IS NULL OR LOWER(p.productCode) = LOWER(:productCode))",
            countQuery = "SELECT COUNT(a) FROM LowStockAlert a JOIN a.product p "
                    + "WHERE (:status IS NULL OR a.status = :status) "
                    + "AND (:includeResolved = true OR :status IS NOT NULL OR a.status <> :resolved) "
                    + "AND a.archived = :archived "
                    + "AND (:productCode IS NULL OR LOWER(p.productCode) = LOWER(:productCode))")
    Page<LowStockAlert> findFiltered(
        @Param("status") LowStockAlertStatusEnum status,
        @Param("resolved") LowStockAlertStatusEnum resolved,
        @Param("includeResolved") boolean includeResolved,
        @Param("archived") boolean archived,
        @Param("productCode") String productCode,
        Pageable pageable
    );
}

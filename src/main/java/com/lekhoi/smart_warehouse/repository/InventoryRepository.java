
package com.lekhoi.smart_warehouse.repository;

import java.util.List;
import java.util.Optional;

import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query(value = "SELECT i FROM Inventory i JOIN FETCH i.product p "
            + "WHERE (:productCode IS NULL OR LOWER(p.productCode) = LOWER(:productCode)) "
            + "AND (:category IS NULL OR LOWER(p.category) = LOWER(:category)) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "AND (LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))) ",
        countQuery = "SELECT COUNT(i) FROM Inventory i JOIN i.product p "
            + "WHERE (:productCode IS NULL OR LOWER(p.productCode) = LOWER(:productCode)) "
            + "AND (:category IS NULL OR LOWER(p.category) = LOWER(:category)) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "AND (LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))) ")
    Page<Inventory> searchInventories(@Param("keyword") String keyword, @Param("productCode") String productCode, @Param("category") String category, @Param("status") ProductStatusEnum status, Pageable pageable);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.product.id = :productId")
    Optional<Inventory> findWithProductByProductId(@Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);
    @Query(value = "SELECT i FROM Inventory i JOIN FETCH i.product")
    List<Inventory> findAllWithProductForReport();

}

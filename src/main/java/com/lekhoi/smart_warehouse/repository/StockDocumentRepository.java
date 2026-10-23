package com.lekhoi.smart_warehouse.repository;

import java.util.Optional;

import com.lekhoi.smart_warehouse.domain.entity.StockDocument;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentTypeEnum;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockDocumentRepository extends JpaRepository<StockDocument, Long> {

    boolean existsByDocumentCode(String documentCode);

    boolean existsByDocumentCodeAndIdNot(String documentCode, Long id);

    @Query(value = "SELECT DISTINCT d FROM StockDocument d "
            + "LEFT JOIN FETCH d.details dt "
            + "LEFT JOIN FETCH dt.product "
            + "WHERE d.id = :id")
    Optional<StockDocument> findWithDetailsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT d FROM StockDocument d WHERE d.id = :id")
    Optional<StockDocument> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT d FROM StockDocument d "
            + "WHERE (:type IS NULL OR d.documentType = :type) "
            + "AND (:status IS NULL OR d.status = :status)",
            countQuery = "SELECT COUNT(d) FROM StockDocument d "
                    + "WHERE (:type IS NULL OR d.documentType = :type) "
                    + "AND (:status IS NULL OR d.status = :status)")
    Page<StockDocument> findFiltered(@Param("type") StockDocumentTypeEnum type, @Param("status") StockDocumentStatusEnum status, Pageable pageable);
}

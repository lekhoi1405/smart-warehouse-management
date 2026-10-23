package com.lekhoi.smart_warehouse.repository;

import java.time.Instant;
import java.util.List;

import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.InventoryTransaction;
import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @Query(value = "SELECT t FROM InventoryTransaction t "
            + "JOIN FETCH t.product "
            + "JOIN FETCH t.documentDetail dt "
            + "WHERE dt.document.id = :documentId "
            + "ORDER BY t.id")
    List<InventoryTransaction> findByDocumentId(@Param("documentId") Long documentId);

    
    @Query(value = "SELECT t FROM InventoryTransaction t "
            + "JOIN FETCH t.product "
            + "JOIN FETCH t.stocktakeDetail sd "
            + "WHERE sd.stocktake.id = :stocktakeId "
            + "ORDER BY t.id")
    List<InventoryTransaction> findByStocktakeId(@Param("stocktakeId") Long stocktakeId);
                        

        @Query(value = "SELECT t FROM InventoryTransaction t JOIN t.documentDetail dt JOIN dt.document d "
                + "WHERE t.createdAt >= :from AND t.createdAt < :to "
                + "AND d.status = :completed "
                + "AND (t.transactionType = :importType OR t.transactionType = :exportType) "
                + "ORDER BY t.createdAt ASC, t.id ASC")
        List<InventoryTransaction> findConfirmedMovementsForReport(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("completed") StockDocumentStatusEnum completed,
        @Param("importType") InventoryTransactionTypeEnum importType,
        @Param("exportType") InventoryTransactionTypeEnum exportType
        );

        @Query(value = "SELECT t FROM InventoryTransaction t JOIN FETCH t.product p "
                + "ORDER BY p.id ASC, t.createdAt ASC, t.id ASC")
        List<InventoryTransaction> findAllOrderedForReport();


}

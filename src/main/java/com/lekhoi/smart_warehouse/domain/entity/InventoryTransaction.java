package com.lekhoi.smart_warehouse.domain.entity;

import java.time.Instant;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;
import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_transaction")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_id")
    private StockDocumentDetail documentDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stocktake_detail_id")
    private StocktakeDetail stocktakeDetail;

    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryTransactionTypeEnum transactionType;

    @Column(nullable = false)
    private Integer quantityBefore;

    @Column(nullable = false)
    private Integer quantityAfter;

    @Column( nullable = false)
    private Integer quantityChange;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }
}

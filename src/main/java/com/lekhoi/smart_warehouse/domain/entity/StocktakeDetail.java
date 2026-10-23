package com.lekhoi.smart_warehouse.domain.entity;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stocktake_detail", uniqueConstraints = @UniqueConstraint(name = "uk_stocktake_product", columnNames = {"stocktake_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
public class StocktakeDetail extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantitySystem;

    @Column(nullable = false)
    private Integer quantityActual;

    @Column(nullable = false)
    private Integer quantityDifference;

    @Column(columnDefinition = "TEXT")
    private String note;
}
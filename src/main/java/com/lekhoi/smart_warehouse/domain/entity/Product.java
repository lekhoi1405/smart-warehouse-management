
package com.lekhoi.smart_warehouse.domain.entity;

import java.time.Instant;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String productCode;

    @Column(nullable = false)
    private String productName;

    private String category;

    @Column(nullable = false)
    private String unit;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatusEnum status;

    @Column(nullable = false)
    private Integer minStockThreshold;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

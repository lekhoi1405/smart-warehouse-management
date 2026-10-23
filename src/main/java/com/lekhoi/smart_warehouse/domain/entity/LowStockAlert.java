package com.lekhoi.smart_warehouse.domain.entity;

import java.time.Instant;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;
import com.lekhoi.smart_warehouse.util.constant.LowStockAlertStatusEnum;

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
@Table(name = "low_stock_alert")
@Getter
@Setter
@NoArgsConstructor
public class LowStockAlert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Long recipientAccountId;

    @Column(nullable = false)
    private Integer quantityAtAlert;

    @Column(nullable = false)
    private Integer thresholdAtAlert;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LowStockAlertStatusEnum status;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }
}

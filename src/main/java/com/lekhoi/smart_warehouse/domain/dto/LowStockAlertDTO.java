package com.lekhoi.smart_warehouse.domain.dto;

import java.time.Instant;

import com.lekhoi.smart_warehouse.util.constant.LowStockAlertStatusEnum;

public interface LowStockAlertDTO {

    record Response(
        Long id,
        Long productId,
        String productCode,
        String productName,
        Long recipientAccountId,
        Integer quantityAtAlert,
        Integer thresholdAtAlert,
        Integer currentQuantity,
        Integer currentThreshold,
        LowStockAlertStatusEnum status,
        boolean archived,
        Instant createdAt,
        Instant readAt,
        Instant closedAt
    ) {}
}
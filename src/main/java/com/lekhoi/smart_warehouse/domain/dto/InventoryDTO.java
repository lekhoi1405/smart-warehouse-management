package com.lekhoi.smart_warehouse.domain.dto;

import java.time.Instant;

import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockLevelEnum;

public interface InventoryDTO {

    record Response(
        Long id,
        Long productId,
        String productCode,
        String productName,
        String unit,
        ProductStatusEnum productStatus,
        Integer quantityOnHand,
        Integer minStockThreshold,
        StockLevelEnum stockLevel,
        Instant updatedAt
    ) {}
}

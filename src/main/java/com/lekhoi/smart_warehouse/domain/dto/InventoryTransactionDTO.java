
package com.lekhoi.smart_warehouse.domain.dto;

import java.time.Instant;

import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;

public interface InventoryTransactionDTO {

    record Response(
        Long id,
        Long productId,
        String productCode,
        Long documentDetailId,
        InventoryTransactionTypeEnum transactionType,
        Integer quantityBefore,
        Integer quantityAfter,
        Integer quantityChange,
        Instant createdAt
    ) {}
}

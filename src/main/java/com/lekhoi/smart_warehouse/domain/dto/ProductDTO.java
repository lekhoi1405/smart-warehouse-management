
package com.lekhoi.smart_warehouse.domain.dto;

import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;

import jakarta.validation.constraints.*;

public interface ProductDTO {

    record CreateRequest(
        @NotBlank String productCode,
        @NotBlank String productName,
        String category,
        @NotBlank String unit,
        String description,
        @NotNull @Min(0) Integer minStockThreshold
    ) {}

    record UpdateRequest(
        @NotNull Long id,
        @NotBlank String productCode,
        @NotBlank String productName,
        String category,
        @NotBlank String unit,
        String description,
        @NotNull ProductStatusEnum status,
        @NotNull @Min(0) Integer minStockThreshold
    ) {}

    record Response(
        Long id,
        String productCode,
        String productName,
        String category,
        String unit,
        String description,
        ProductStatusEnum status,
        Integer minStockThreshold
    ) {}
}

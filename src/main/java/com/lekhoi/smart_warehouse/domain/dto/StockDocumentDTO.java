package com.lekhoi.smart_warehouse.domain.dto;

import java.time.LocalDate;
import java.util.List;

import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentTypeEnum;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public interface StockDocumentDTO {

    record DetailRequest(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity
    ) {}

    record CreateRequest(
        @NotBlank String documentCode,
        @NotNull StockDocumentTypeEnum documentType,
        Long supplierId,
        @NotNull LocalDate documentDate,
        String note,
        @NotEmpty List<@Valid DetailRequest> details
    ) {}

    record UpdateRequest(
        @NotNull Long id,
        @NotBlank String documentCode,
        @NotNull StockDocumentTypeEnum documentType,
        Long supplierId,
        @NotNull LocalDate documentDate,
        String note,
        @NotEmpty List<@Valid DetailRequest> details
    ) {}

    record DetailResponse(
        Long id,
        Long productId,
        String productCode,
        Integer quantity
    ) {}

    record Response(
        Long id,
        String documentCode,
        StockDocumentTypeEnum documentType,
        Long supplierId,
        Long createdBy,
        LocalDate documentDate,
        StockDocumentStatusEnum status,
        String note,
        List<DetailResponse> details
    ) {}

    record SummaryResponse(
        Long id,
        String documentCode,
        StockDocumentTypeEnum documentType,
        Long supplierId,
        LocalDate documentDate,
        StockDocumentStatusEnum status
    ) {}
}

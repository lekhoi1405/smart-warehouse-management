package com.lekhoi.smart_warehouse.domain.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lekhoi.smart_warehouse.util.constant.StocktakeStatusEnum;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public interface StocktakeDTO {

    record DetailRequest(
        @NotNull Long productId,
        @NotNull @Min(0) Integer quantityActual,
        String note
    ) {}

    record CreateRequest(
        @NotBlank String stocktakeCode,
        @NotNull LocalDate stocktakeDate,
        String note,
        @NotEmpty List<@Valid DetailRequest> details
    ) {}

    record CountUpdateRequest(
        @NotNull Long productId,
        @NotNull @Min(0) Integer quantityActual,
        String note
    ) {}

    record UpdateRequest(
        @NotNull Long id,
        @NotEmpty List<@Valid CountUpdateRequest> details
    ) {}

    record DetailResponse(
        Long id,
        Long productId,
        String productCode,
        Integer quantitySystem,
        Integer quantityActual,
        Integer quantityDifference,
        String note
    ) {}

    record Response(
        Long id,
        String stocktakeCode,
        Long createdBy,
        LocalDate stocktakeDate,
        StocktakeStatusEnum status,
        String note,
        List<DetailResponse> details
    ) {}

    record SummaryResponse(
        Long id,
        String stocktakeCode,
        LocalDate stocktakeDate,
        StocktakeStatusEnum status
    ) {}

    record TransactionResponse(
        Long id,
        Long productId,
        String productCode,
        Long stocktakeDetailId,
        Integer quantityBefore,
        Integer quantityAfter,
        Integer quantityChange,
        Instant createdAt
    ) {}
}
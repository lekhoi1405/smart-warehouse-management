package com.lekhoi.smart_warehouse.domain.dto;

import java.time.LocalDate;
import java.util.List;

import com.lekhoi.smart_warehouse.util.constant.StatisticsGroupEnum;
import com.lekhoi.smart_warehouse.util.constant.StockPeriodEnum;

public interface StatisticsDTO {

    record MovementRow(
        String period,
        long imported,
        long exported
    ) {}

    record MovementResponse(
        StatisticsGroupEnum groupBy,
        LocalDate from,
        LocalDate to,
        long totalImported,
        long totalExported,
        List<MovementRow> rows
    ) {}

    record ClosingStockRow(
        Long productId,
        String productCode,
        String productName,
        String unit,
        Integer closingQuantity
    ) {}

    record ClosingStockResponse(
        StockPeriodEnum periodType,
        int year,
        Integer month,
        LocalDate periodEnd,
        List<ClosingStockRow> rows
    ) {}
}

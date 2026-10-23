package com.lekhoi.smart_warehouse.controller;

import java.time.LocalDate;

import com.lekhoi.smart_warehouse.domain.dto.StatisticsDTO;
import com.lekhoi.smart_warehouse.service.StatisticsService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;
import com.lekhoi.smart_warehouse.util.constant.StatisticsGroupEnum;
import com.lekhoi.smart_warehouse.util.constant.StockPeriodEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @ApiMessage("Get import and export statistics")
    @GetMapping("/movements")
    public ResponseEntity<StatisticsDTO.MovementResponse> getMovements(
        @RequestParam(name = "groupBy") StatisticsGroupEnum groupBy,
        @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(statisticsService.handleGetMovements(groupBy, from, to));
    }

    @ApiMessage("Get closing stock statistics")
    @GetMapping("/closing-stock")
    public ResponseEntity<StatisticsDTO.ClosingStockResponse> getClosingStock(
        @RequestParam(name = "periodType") StockPeriodEnum periodType,
        @RequestParam(name = "year") int year,
        @RequestParam(name = "month", required = false) Integer month) {

        return ResponseEntity.ok(statisticsService.handleGetClosingStock(periodType, year, month));
    }
}

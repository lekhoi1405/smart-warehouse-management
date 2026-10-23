package com.lekhoi.smart_warehouse.controller;

import java.time.LocalDate;

import com.lekhoi.smart_warehouse.service.ReportService;
import com.lekhoi.smart_warehouse.util.constant.ReportFormatEnum;
import com.lekhoi.smart_warehouse.util.constant.StatisticsGroupEnum;
import com.lekhoi.smart_warehouse.util.constant.StockPeriodEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/movements")
    public ResponseEntity<byte[]> downloadMovements(
        @RequestParam(name = "groupBy") StatisticsGroupEnum groupBy,
        @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(name = "format") ReportFormatEnum format) {

        return download(reportService.handleExportMovements(groupBy, from, to, format));
    }

    @GetMapping("/closing-stock")
    public ResponseEntity<byte[]> downloadClosingStock(
        @RequestParam(name = "periodType") StockPeriodEnum periodType,
        @RequestParam(name = "year") int year,
        @RequestParam(name = "month", required = false) Integer month,
        @RequestParam(name = "format") ReportFormatEnum format) {

        return download(reportService.handleExportClosingStock(periodType, year, month, format));
    }

    private ResponseEntity<byte[]> download(ReportService.ReportFile file) {
        ContentDisposition disposition = ContentDisposition.attachment().filename(file.fileName()).build();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(file.contentType())
            .contentLength(file.content().length)
            .body(file.content());
    }
}

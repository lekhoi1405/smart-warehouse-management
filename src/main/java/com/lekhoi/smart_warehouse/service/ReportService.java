package com.lekhoi.smart_warehouse.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lekhoi.smart_warehouse.domain.dto.StatisticsDTO;
import com.lekhoi.smart_warehouse.util.constant.ReportFormatEnum;
import com.lekhoi.smart_warehouse.util.constant.StatisticsGroupEnum;
import com.lekhoi.smart_warehouse.util.constant.StockPeriodEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final StatisticsService statisticsService;
    private final ObjectMapper objectMapper;

    public record ReportFile(String fileName, MediaType contentType, byte[] content) {}

    public ReportFile handleExportMovements(StatisticsGroupEnum groupBy, LocalDate from, LocalDate to, ReportFormatEnum format) {
        StatisticsDTO.MovementResponse report = statisticsService.handleGetMovements(groupBy, from, to);
        String name = "movements-" + groupBy + "-" + from + "-" + to;
        validateFormat(format);

        if (format == ReportFormatEnum.JSON) return toJson(report, name);

        StringBuilder csv = new StringBuilder("period,imported,exported\r\n");

        for (StatisticsDTO.MovementRow row : report.rows()) {
            csv.append(quoted(row.period())).append(",")
                .append(row.imported()).append(",")
                .append(row.exported()).append("\r\n");
        }

        csv.append("TOTAL,").append(report.totalImported()).append(",").append(report.totalExported()).append("\r\n");

        return toCsv(csv.toString(), name);
    }

    public ReportFile handleExportClosingStock(StockPeriodEnum periodType, int year, Integer month, ReportFormatEnum format) {
        StatisticsDTO.ClosingStockResponse report = statisticsService.handleGetClosingStock(periodType, year, month);
        String name = "closing-stock-" + periodType + "-" + report.periodEnd();
        validateFormat(format);

        if (format == ReportFormatEnum.JSON) return toJson(report, name);

        StringBuilder csv = new StringBuilder("productId,productCode,productName,unit,closingQuantity\r\n");

        for (StatisticsDTO.ClosingStockRow row : report.rows()) {
            csv.append(row.productId()).append(",")
                .append(quoted(row.productCode())).append(",")
                .append(quoted(row.productName())).append(",")
                .append(quoted(row.unit())).append(",")
                .append(row.closingQuantity()).append("\r\n");
        }

        return toCsv(csv.toString(), name);
    }

    private void validateFormat(ReportFormatEnum format) {
        if (format == null) throw new IllegalArgumentException("Report format is required");
    }

    private ReportFile toCsv(String content, String name) {
        byte[] data = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        return new ReportFile(name + ".csv", MediaType.parseMediaType("text/csv;charset=UTF-8"), data);
    }

    private ReportFile toJson(Object report, String name) {
        try {
            return new ReportFile(name + ".json", MediaType.APPLICATION_JSON, objectMapper.writeValueAsBytes(report));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Report file generation failed", ex);
        }
    }

    private String quoted(String value) {
        String safe = value == null ? "" : value;
        String check = safe.stripLeading();

        if (!check.isEmpty() && "=+-@".indexOf(check.charAt(0)) >= 0) safe = "'" + safe;

        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}

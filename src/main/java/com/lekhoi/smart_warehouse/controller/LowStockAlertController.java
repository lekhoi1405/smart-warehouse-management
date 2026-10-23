package com.lekhoi.smart_warehouse.controller;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.LowStockAlertDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.service.LowStockAlertService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;
import com.lekhoi.smart_warehouse.util.constant.LowStockAlertStatusEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/low-stock-alerts")
public class LowStockAlertController {

    private final LowStockAlertService alertService;

    @ApiMessage("Fetch low stock alerts")
    @GetMapping
    public ResponseEntity<ResultPagination<List<LowStockAlertDTO.Response>>> getAlerts(
        @RequestParam(name = "current", defaultValue = "1") int current,
        @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(name = "status", required = false) LowStockAlertStatusEnum status,
        @RequestParam(name = "includeResolved", defaultValue = "false") boolean includeResolved,
        @RequestParam(name = "archived", defaultValue = "false") boolean archived,
        @RequestParam(name = "productCode", required = false) String productCode) {

        return ResponseEntity.ok(alertService.handleGetAllAlerts(current, pageSize, status, includeResolved, archived, productCode));
    }

    @ApiMessage("Fetch low stock alert")
    @GetMapping("/{id}")
    public ResponseEntity<LowStockAlertDTO.Response> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(alertService.handleGetAlertById(id));
    }

    @ApiMessage("Synchronize low stock alerts")
    @PostMapping("/synchronize")
    public ResponseEntity<Integer> synchronize() {
        return ResponseEntity.ok(alertService.handleSynchronizeAll());
    }

    @ApiMessage("Mark alert as read")
    @PostMapping("/{id}/read")
    public ResponseEntity<LowStockAlertDTO.Response> markAsRead(@PathVariable("id") Long id) {
        return ResponseEntity.ok(alertService.handleMarkAsRead(id));
    }

    @ApiMessage("Mark alert as unread")
    @PostMapping("/{id}/unread")
    public ResponseEntity<LowStockAlertDTO.Response> markAsUnread(@PathVariable("id") Long id) {
        return ResponseEntity.ok(alertService.handleMarkAsUnread(id));
    }

    @ApiMessage("Start processing alert")
    @PostMapping("/{id}/process")
    public ResponseEntity<LowStockAlertDTO.Response> startProcessing(@PathVariable("id") Long id) {
        return ResponseEntity.ok(alertService.handleStartProcessing(id));
    }

    @ApiMessage("Archive alert")
    @PostMapping("/{id}/archive")
    public ResponseEntity<LowStockAlertDTO.Response> archive(@PathVariable("id") Long id) {
        return ResponseEntity.ok(alertService.handleArchiveAlert(id));
    }

    @ApiMessage("Restore alert")
    @PostMapping("/{id}/restore")
    public ResponseEntity<LowStockAlertDTO.Response> restore(@PathVariable("id") Long id) {
        return ResponseEntity.ok(alertService.handleRestoreAlert(id));
    }
}

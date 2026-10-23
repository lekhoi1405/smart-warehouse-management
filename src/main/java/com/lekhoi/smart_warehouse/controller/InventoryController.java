package com.lekhoi.smart_warehouse.controller;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.InventoryDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.service.InventoryService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @ApiMessage("Search inventories")
    @GetMapping
    public ResponseEntity<ResultPagination<List<InventoryDTO.Response>>> getInventories(
        @RequestParam(name = "current", defaultValue = "1") int current,
        @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(name = "keyword", defaultValue = "") String keyword,
        @RequestParam(name = "productCode", required = false) String productCode,
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "status", required = false) ProductStatusEnum status) {

        return ResponseEntity.ok(inventoryService.handleSearchInventories(current, pageSize, keyword, productCode, category, status));
    }

    @ApiMessage("Fetch inventory by product")
    @GetMapping("/products/{productId}")
    public ResponseEntity<InventoryDTO.Response> getInventoryByProductId(@PathVariable("productId") Long productId) {
        return ResponseEntity.ok(inventoryService.handleGetInventoryByProductId(productId));
    }
}

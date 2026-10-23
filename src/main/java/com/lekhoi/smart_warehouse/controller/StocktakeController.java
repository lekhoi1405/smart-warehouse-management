package com.lekhoi.smart_warehouse.controller;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.StocktakeDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.service.StocktakeService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;
import com.lekhoi.smart_warehouse.util.constant.StocktakeStatusEnum;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stocktakes")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @ApiMessage("Create stocktake")
    @PostMapping
    public ResponseEntity<StocktakeDTO.Response> create(@Valid @RequestBody StocktakeDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stocktakeService.handleCreateStocktake(request));
    }

    @ApiMessage("Update stocktake counts")
    @PutMapping
    public ResponseEntity<StocktakeDTO.Response> update(@Valid @RequestBody StocktakeDTO.UpdateRequest request) {
        return ResponseEntity.ok(stocktakeService.handleUpdateDraft(request));
    }

    @ApiMessage("Confirm stocktake")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<StocktakeDTO.Response> confirm(@PathVariable("id") Long id) {
        return ResponseEntity.ok(stocktakeService.handleConfirmStocktake(id));
    }

    @ApiMessage("Cancel stocktake")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<StocktakeDTO.Response> cancel(@PathVariable("id") Long id) {
        return ResponseEntity.ok(stocktakeService.handleCancelStocktake(id));
    }

    @ApiMessage("Fetch stocktake")
    @GetMapping("/{id}")
    public ResponseEntity<StocktakeDTO.Response> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(stocktakeService.handleGetStocktakeById(id));
    }

    @ApiMessage("Fetch stocktakes")
    @GetMapping
    public ResponseEntity<ResultPagination<List<StocktakeDTO.SummaryResponse>>> getAll(
        @RequestParam(name = "current", defaultValue = "1") int current,
        @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(name = "status", required = false) StocktakeStatusEnum status) {

        return ResponseEntity.ok(stocktakeService.handleGetAllStocktakes(current, pageSize, status));
    }

    @ApiMessage("Fetch stocktake transactions")
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<StocktakeDTO.TransactionResponse>> getTransactions(@PathVariable("id") Long id) {
        return ResponseEntity.ok(stocktakeService.handleGetTransactions(id));
    }
}

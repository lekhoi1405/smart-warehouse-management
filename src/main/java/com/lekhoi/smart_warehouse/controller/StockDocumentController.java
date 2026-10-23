package com.lekhoi.smart_warehouse.controller;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.InventoryTransactionDTO;
import com.lekhoi.smart_warehouse.domain.dto.StockDocumentDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.service.StockDocumentService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentTypeEnum;

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
@RequestMapping("/stock-documents")
public class StockDocumentController {

    private final StockDocumentService documentService;

    @ApiMessage("Create stock document")
    @PostMapping
    public ResponseEntity<StockDocumentDTO.Response> create(@Valid @RequestBody StockDocumentDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.handleCreateDocument(request));
    }

    @ApiMessage("Update draft document")
    @PutMapping
    public ResponseEntity<StockDocumentDTO.Response> update(@Valid @RequestBody StockDocumentDTO.UpdateRequest request) {
        return ResponseEntity.ok(documentService.handleUpdateDraft(request));
    }

    @ApiMessage("Submit stock document")
    @PostMapping("/{id}/submit")
    public ResponseEntity<StockDocumentDTO.Response> submit(@PathVariable("id") Long id) {
        return ResponseEntity.ok(documentService.handleSubmitDocument(id));
    }

    @ApiMessage("Return document to draft")
    @PostMapping("/{id}/return")
    public ResponseEntity<StockDocumentDTO.Response> returnToDraft(@PathVariable("id") Long id) {
        return ResponseEntity.ok(documentService.handleReturnToDraft(id));
    }

    @ApiMessage("Confirm stock document")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<StockDocumentDTO.Response> confirm(@PathVariable("id") Long id) {
        return ResponseEntity.ok(documentService.handleConfirmDocument(id));
    }

    @ApiMessage("Cancel stock document")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<StockDocumentDTO.Response> cancel(@PathVariable("id") Long id) {
        return ResponseEntity.ok(documentService.handleCancelDocument(id));
    }

    @ApiMessage("Get stock document")
    @GetMapping("/{id}")
    public ResponseEntity<StockDocumentDTO.Response> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(documentService.handleGetDocumentById(id));
    }

    @ApiMessage("Get stock documents")
    @GetMapping
    public ResponseEntity<ResultPagination<List<StockDocumentDTO.SummaryResponse>>> getAll(
        @RequestParam(name = "current", defaultValue = "1") int current,
        @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(name = "type", required = false) StockDocumentTypeEnum type,
        @RequestParam(name = "status", required = false) StockDocumentStatusEnum status) {

        return ResponseEntity.ok(documentService.handleGetAllDocuments(current, pageSize, type, status));
    }

    @ApiMessage("Get document transactions")
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<InventoryTransactionDTO.Response>> getTransactions(@PathVariable("id") Long id) {
        return ResponseEntity.ok(documentService.handleGetTransactions(id));
    }
}

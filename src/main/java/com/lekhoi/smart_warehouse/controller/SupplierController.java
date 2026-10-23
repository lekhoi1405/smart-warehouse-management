package com.lekhoi.smart_warehouse.controller;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.SupplierDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.service.SupplierService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;
import com.lekhoi.smart_warehouse.util.constant.SupplierStatusEnum;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    @ApiMessage("Create supplier")
    @PostMapping
    public ResponseEntity<SupplierDTO.Response> createSupplier(@Valid @RequestBody SupplierDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.handleCreateSupplier(request));
    }

    @ApiMessage("Fetch supplier")
    @GetMapping("/{id}")
    public ResponseEntity<SupplierDTO.Response> getSupplier(@PathVariable("id") Long id) {
        return ResponseEntity.ok(supplierService.handleGetSupplierById(id));
    }

    @ApiMessage("Fetch all suppliers")
    @GetMapping
    public ResponseEntity<ResultPagination<List<SupplierDTO.Response>>> getSuppliers(
        @RequestParam(name = "current", defaultValue = "1") int current,
        @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(name = "keyword", defaultValue = "") String keyword,
        @RequestParam(name = "status", required = false) SupplierStatusEnum status) {

        return ResponseEntity.ok(supplierService.handleGetAllSuppliers(current, pageSize, keyword, status));
    }

    @ApiMessage("Update supplier")
    @PutMapping
    public ResponseEntity<SupplierDTO.Response> updateSupplier(@Valid @RequestBody SupplierDTO.UpdateRequest request) {
        return ResponseEntity.ok(supplierService.handleUpdateSupplier(request));
    }

    @ApiMessage("Deactivate supplier")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable("id") Long id) {
        supplierService.handleDeleteSupplier(id);
        return ResponseEntity.ok().build();
    }
}
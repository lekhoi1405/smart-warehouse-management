
package com.lekhoi.smart_warehouse.controller;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.ProductDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.service.ProductService;
import com.lekhoi.smart_warehouse.util.annotation.ApiMessage;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    @ApiMessage("Create product")
    @PostMapping
    public ResponseEntity<ProductDTO.Response> createProduct(
            @Valid @RequestBody ProductDTO.CreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(productService.handleCreateProduct(request));
    }

    @ApiMessage("Fetch product")
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO.Response> getProduct(
            @PathVariable("id") Long id) {

        return ResponseEntity.ok(
            productService.handleGetProductById(id)
        );
    }

    @ApiMessage("Fetch all products")
    @GetMapping
    public ResponseEntity<ResultPagination<List<ProductDTO.Response>>> getProducts(
            @RequestParam(name = "current", defaultValue = "1")
            int current,

            @RequestParam(name = "pageSize", defaultValue = "10")
            int pageSize,

            @RequestParam(name = "keyword", defaultValue = "")
            String keyword,

            @RequestParam(name = "category", required = false)
            String category,

            @RequestParam(name = "status", required = false)
            ProductStatusEnum status) {

        return ResponseEntity.ok(
            productService.handleGetAllProducts(
                current, pageSize, keyword, category, status
            )
        );
    }

    @ApiMessage("Update product")
    @PutMapping
    public ResponseEntity<ProductDTO.Response> updateProduct(
            @Valid @RequestBody ProductDTO.UpdateRequest request) {

        return ResponseEntity.ok(
            productService.handleUpdateProduct(request)
        );
    }

    @ApiMessage("Deactivate product")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable("id") Long id) {

        productService.handleDeleteProduct(id);
        return ResponseEntity.ok().build();
    }
}

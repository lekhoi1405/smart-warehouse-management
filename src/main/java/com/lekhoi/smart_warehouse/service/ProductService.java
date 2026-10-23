
package com.lekhoi.smart_warehouse.service;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.ProductDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination.Meta;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.repository.ProductRepository;
import com.lekhoi.smart_warehouse.service.mapper.ProductMapper;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final InventoryService inventoryService;
    private final LowStockAlertService lowStockAlertService;

    @Transactional
    public ProductDTO.Response handleCreateProduct(ProductDTO.CreateRequest request) {
        String productCode = request.productCode().trim();

        if (productRepository.existsByProductCode(productCode)) {
            throw new AlreadyExistsException("Product code already exists");
        }

        Product product = productMapper.toEntity(request);
        product.setProductCode(productCode);
        product.setStatus(ProductStatusEnum.ACTIVE);

        Product saved = productRepository.save(product);
        inventoryService.initializeForProduct(saved);
        lowStockAlertService.synchronizeForProduct(saved.getId());

        return productMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductDTO.Response handleGetProductById(Long id) {

        Product product = productRepository.findById(id)
            .orElseThrow(() ->
                new IdInvalidException(
                    "Product not found: " + id
                )
            );

        return productMapper.toResponse(product);
    }

    @Transactional(readOnly = true)
    public ResultPagination<List<ProductDTO.Response>> handleGetAllProducts(
            int current,
            int pageSize,
            String keyword,
            String category,
            ProductStatusEnum status) {

        if (current < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException(
                "current must be >= 1 and pageSize must be 1..100"
            );
        }

        Pageable pageable = PageRequest.of(
            current - 1,
            pageSize,
            Sort.by("id").ascending()
        );

        String searchKeyword =
            keyword == null ? "" : keyword.trim();

        String categoryFilter =
            category == null || category.isBlank()
                ? null
                : category.trim();

        ProductStatusEnum effectiveStatus =
            status == null ? ProductStatusEnum.ACTIVE : status;

        Page<Product> productPage =
            productRepository.searchProducts(
                searchKeyword,
                categoryFilter,
                effectiveStatus,
                pageable
            );

        List<ProductDTO.Response> responses =
            productPage.getContent()
                .stream()
                .map(productMapper::toResponse)
                .toList();

        Meta meta = Meta.builder()
            .current(productPage.getNumber() + 1)
            .pageSize(productPage.getSize())
            .pages(productPage.getTotalPages())
            .total(productPage.getTotalElements())
            .build();

        return ResultPagination.<List<ProductDTO.Response>>builder()
            .result(responses)
            .meta(meta)
            .build();
    }

    @Transactional
    public ProductDTO.Response handleUpdateProduct(
            ProductDTO.UpdateRequest request) {

        Product product = productRepository.findById(request.id())
            .orElseThrow(() ->
                new IdInvalidException(
                    "Product not found: " + request.id()
                )
            );
        String productCode = request.productCode().trim();

        if (!product.getProductCode().equals(productCode) && productRepository.existsByProductCodeAndIdNot(productCode, product.getId())) {
            throw new AlreadyExistsException("Product code already exists");
        }

        productMapper.update(request, product);
        product.setProductCode(productCode);

        lowStockAlertService.synchronizeForProduct(product.getId());

        return productMapper.toResponse(product);
    }

    @Transactional
    public void handleDeleteProduct(Long id) {

        Product product = productRepository.findById(id)
            .orElseThrow(() ->
                new IdInvalidException(
                    "Product not found: " + id
                )
            );

        product.setStatus(ProductStatusEnum.INACTIVE);
    }
}

package com.lekhoi.smart_warehouse.service;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.InventoryDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination.Meta;
import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockLevelEnum;
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
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public void initializeForProduct(Product product) {
        Inventory inventory = Inventory.builder().product(product).quantityOnHand(0).build();
        inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public InventoryDTO.Response handleGetInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findWithProductByProductId(productId)
            .orElseThrow(() -> new IdInvalidException("Inventory not found for product: " + productId));

        return toResponse(inventory);
    }

    @Transactional(readOnly = true)
    public ResultPagination<List<InventoryDTO.Response>> handleSearchInventories(int current, int pageSize, String keyword, String productCode, String category, ProductStatusEnum status) {
        if (current < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("current must be >= 1 and pageSize must be between 1 and 100");
        }

        String searchKeyword = keyword == null ? "" : keyword.trim();
        String searchCode = productCode == null || productCode.isBlank() ? null : productCode.trim();
        String searchCategory = category == null || category.isBlank() ? null : category.trim();

        Pageable pageable = PageRequest.of(current - 1, pageSize, Sort.by("id").ascending());
        Page<Inventory> inventoryPage = inventoryRepository.searchInventories(searchKeyword, searchCode, searchCategory, status, pageable);

        List<InventoryDTO.Response> responses = inventoryPage.getContent().stream().map(this::toResponse).toList();

        Meta meta = Meta.builder()
            .current(inventoryPage.getNumber() + 1)
            .pageSize(inventoryPage.getSize())
            .pages(inventoryPage.getTotalPages())
            .total(inventoryPage.getTotalElements())
            .build();

        return ResultPagination.<List<InventoryDTO.Response>>builder().result(responses).meta(meta).build();
    }

    private InventoryDTO.Response toResponse(Inventory inventory) {
        Product product = inventory.getProduct();
        int quantity = inventory.getQuantityOnHand();
        int threshold = product.getMinStockThreshold();

        StockLevelEnum stockLevel = quantity == 0 ? StockLevelEnum.OUT_OF_STOCK
            : quantity <= threshold ? StockLevelEnum.LOW : StockLevelEnum.NORMAL;

        return new InventoryDTO.Response(
            inventory.getId(),
            product.getId(),
            product.getProductCode(),
            product.getProductName(),
            product.getUnit(),
            product.getStatus(),
            quantity,
            threshold,
            stockLevel,
            inventory.getUpdatedAt()
        );
    }
}

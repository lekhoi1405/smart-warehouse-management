package com.lekhoi.smart_warehouse.service;

import java.time.Instant;
import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.LowStockAlertDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination.Meta;
import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.LowStockAlert;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.repository.LowStockAlertRepository;
import com.lekhoi.smart_warehouse.util.constant.LowStockAlertStatusEnum;
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
public class LowStockAlertService {

    private final LowStockAlertRepository alertRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public void synchronizeForProduct(Long productId) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new IdInvalidException("Inventory not found for product: " + productId));

        Product product = inventory.getProduct();
        int quantity = inventory.getQuantityOnHand();
        int threshold = product.getMinStockThreshold();

        if (quantity < 0 || threshold < 0) {
            throw new IllegalStateException("Invalid stock or threshold for product: " + productId);
        }

        List<LowStockAlert> openAlerts = alertRepository.findOpenByProductIdForUpdate(productId, LowStockAlertStatusEnum.RESOLVED);

        if (quantity <= threshold) {
            if (openAlerts.isEmpty()) {
                LowStockAlert alert = new LowStockAlert();
                alert.setProduct(product);
                alert.setQuantityAtAlert(quantity);
                alert.setThresholdAtAlert(threshold);
                alert.setStatus(LowStockAlertStatusEnum.NEW);
                alertRepository.save(alert);
            }
        } else {
            for (LowStockAlert alert : openAlerts) {
                alert.setStatus(LowStockAlertStatusEnum.RESOLVED);
                alert.setClosedAt(Instant.now());
            }
        }
    }

    @Transactional
    public int handleSynchronizeAll() {
        List<Long> productIds = inventoryRepository.findAll().stream()
            .map(inventory -> inventory.getProduct().getId())
            .sorted()
            .toList();

        for (Long productId : productIds) {
            synchronizeForProduct(productId);
        }

        return productIds.size();
    }

    @Transactional(readOnly = true)
    public LowStockAlertDTO.Response handleGetAlertById(Long id) {
        LowStockAlert alert = alertRepository.findWithProductById(id)
            .orElseThrow(() -> new IdInvalidException("Alert not found: " + id));

        return toResponse(alert);
    }

    @Transactional(readOnly = true)
    public ResultPagination<List<LowStockAlertDTO.Response>> handleGetAllAlerts(int current, int pageSize, LowStockAlertStatusEnum status, boolean includeResolved, boolean archived, String productCode) {
        if (current < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        String code = productCode == null || productCode.isBlank() ? null : productCode.trim();
        Pageable pageable = PageRequest.of(current - 1, pageSize, Sort.by("id").descending());

        Page<LowStockAlert> page = alertRepository.findFiltered(status, LowStockAlertStatusEnum.RESOLVED, includeResolved, archived, code, pageable);

        List<LowStockAlertDTO.Response> responses = page.getContent().stream()
            .map(this::toResponse)
            .toList();

        Meta meta = Meta.builder()
            .current(page.getNumber() + 1)
            .pageSize(page.getSize())
            .pages(page.getTotalPages())
            .total(page.getTotalElements())
            .build();

        return ResultPagination.<List<LowStockAlertDTO.Response>>builder()
            .result(responses)
            .meta(meta)
            .build();
    }

    @Transactional
    public LowStockAlertDTO.Response handleMarkAsRead(Long id) {
        LowStockAlert alert = getLockedAlert(id);

        if (alert.getStatus() == LowStockAlertStatusEnum.NEW) {
            alert.setStatus(LowStockAlertStatusEnum.READ);
            alert.setReadAt(Instant.now());
        } else if (alert.getStatus() == LowStockAlertStatusEnum.RESOLVED) {
            throw new IllegalStateException("Resolved alert cannot be marked as read");
        }

        return toResponse(alert);
    }

    @Transactional
    public LowStockAlertDTO.Response handleMarkAsUnread(Long id) {
        LowStockAlert alert = getLockedAlert(id);

        if (alert.getStatus() == LowStockAlertStatusEnum.READ) {
            alert.setStatus(LowStockAlertStatusEnum.NEW);
            alert.setReadAt(null);
        } else if (alert.getStatus() != LowStockAlertStatusEnum.NEW) {
            throw new IllegalStateException("Only new or read alerts can be marked as unread");
        }

        return toResponse(alert);
    }

    @Transactional
    public LowStockAlertDTO.Response handleStartProcessing(Long id) {
        LowStockAlert alert = getLockedAlert(id);

        if (alert.getStatus() == LowStockAlertStatusEnum.READ) {
            alert.setStatus(LowStockAlertStatusEnum.PROCESSING);
        } else if (alert.getStatus() != LowStockAlertStatusEnum.PROCESSING) {
            throw new IllegalStateException("Alert must be read before processing");
        }

        return toResponse(alert);
    }

    @Transactional
    public LowStockAlertDTO.Response handleArchiveAlert(Long id) {
        LowStockAlert alert = getLockedAlert(id);
        alert.setArchived(true);
        return toResponse(alert);
    }

    @Transactional
    public LowStockAlertDTO.Response handleRestoreAlert(Long id) {
        LowStockAlert alert = getLockedAlert(id);
        alert.setArchived(false);
        return toResponse(alert);
    }

    private LowStockAlert getLockedAlert(Long id) {
        return alertRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IdInvalidException("Alert not found: " + id));
    }

    private LowStockAlertDTO.Response toResponse(LowStockAlert alert) {
        Product product = alert.getProduct();

        Inventory inventory = inventoryRepository.findWithProductByProductId(product.getId())
            .orElseThrow(() -> new IdInvalidException("Inventory not found for product: " + product.getId()));

        return new LowStockAlertDTO.Response(
            alert.getId(),
            product.getId(),
            product.getProductCode(),
            product.getProductName(),
            alert.getRecipientAccountId(),
            alert.getQuantityAtAlert(),
            alert.getThresholdAtAlert(),
            inventory.getQuantityOnHand(),
            product.getMinStockThreshold(),
            alert.getStatus(),
            alert.isArchived(),
            alert.getCreatedAt(),
            alert.getReadAt(),
            alert.getClosedAt()
        );
    }
}

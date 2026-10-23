package com.lekhoi.smart_warehouse.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lekhoi.smart_warehouse.domain.dto.StocktakeDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination.Meta;
import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.InventoryTransaction;
import com.lekhoi.smart_warehouse.domain.entity.Stocktake;
import com.lekhoi.smart_warehouse.domain.entity.StocktakeDetail;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.repository.InventoryTransactionRepository;
import com.lekhoi.smart_warehouse.repository.StocktakeRepository;
import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.StocktakeStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StocktakeService {

    private final StocktakeRepository stocktakeRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final LowStockAlertService lowStockAlertService;  
    private final AuthorityService authorityService;

    @Transactional
    public StocktakeDTO.Response handleCreateStocktake(StocktakeDTO.CreateRequest request) {
        String code = request.stocktakeCode().trim();

        if (code.isBlank() || request.stocktakeDate() == null || request.details() == null || request.details().isEmpty()) {
            throw new IllegalArgumentException("Invalid stocktake information");
        }

        if (stocktakeRepository.existsByStocktakeCode(code)) {
            throw new AlreadyExistsException("Stocktake code already exists");
        }

        Stocktake stocktake = new Stocktake();
        stocktake.setStocktakeCode(code);
        stocktake.setStocktakeDate(request.stocktakeDate());
        stocktake.setStatus(StocktakeStatusEnum.DRAFT);
        stocktake.setNote(request.note());
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        stocktake.setCreatedBy(authorityService.loadActive(username).id());
        
        Set<Long> productIds = new HashSet<>();

        for (StocktakeDTO.DetailRequest requestDetail : request.details()) {
            Long productId = requestDetail.productId();

            if (productId == null || requestDetail.quantityActual() == null || requestDetail.quantityActual() < 0) {
                throw new IllegalArgumentException("Invalid stocktake detail");
            }

            if (!productIds.add(productId)) {
                throw new IllegalArgumentException("Duplicate product in stocktake: " + productId);
            }

            Inventory inventory = inventoryRepository.findWithProductByProductId(productId)
                .orElseThrow(() -> new IdInvalidException("Product inventory not found: " + productId));

            int quantitySystem = inventory.getQuantityOnHand();
            int quantityActual = requestDetail.quantityActual();

            if (quantitySystem < 0) {
                throw new IllegalStateException("Invalid inventory quantity: " + productId);
            }

            StocktakeDetail detail = new StocktakeDetail();
            detail.setStocktake(stocktake);
            detail.setProduct(inventory.getProduct());
            detail.setQuantitySystem(quantitySystem);
            detail.setQuantityActual(quantityActual);
            detail.setQuantityDifference(Math.subtractExact(quantityActual, quantitySystem));
            detail.setNote(requestDetail.note());

            stocktake.getDetails().add(detail);
        }

        Stocktake saved = stocktakeRepository.saveAndFlush(stocktake);
        return toResponse(saved);
    }

    @Transactional
    public StocktakeDTO.Response handleUpdateDraft(StocktakeDTO.UpdateRequest request) {
        Stocktake stocktake = getLockedStocktake(request.id());
        requireDraft(stocktake);

        if (request.details() == null || request.details().size() != stocktake.getDetails().size()) {
            throw new IllegalArgumentException("All existing products must be included in the update");
        }

        Map<Long, StocktakeDetail> detailsByProduct = new HashMap<>();

        for (StocktakeDetail detail : stocktake.getDetails()) {
            detailsByProduct.put(detail.getProduct().getId(), detail);
        }

        Set<Long> updatedProductIds = new HashSet<>();

        for (StocktakeDTO.CountUpdateRequest requestDetail : request.details()) {
            Long productId = requestDetail.productId();

            if (productId == null || requestDetail.quantityActual() == null || requestDetail.quantityActual() < 0) {
                throw new IllegalArgumentException("Invalid stocktake count");
            }

            if (!updatedProductIds.add(productId)) {
                throw new IllegalArgumentException("Duplicate product in request: " + productId);
            }

            StocktakeDetail detail = detailsByProduct.get(productId);

            if (detail == null) {
                throw new IdInvalidException("Product is not part of this stocktake: " + productId);
            }

            detail.setQuantityActual(requestDetail.quantityActual());
            detail.setQuantityDifference(Math.subtractExact(requestDetail.quantityActual(), detail.getQuantitySystem()));
            detail.setNote(requestDetail.note());
        }

        return toResponse(stocktake);
    }

    @Transactional
    public StocktakeDTO.Response handleConfirmStocktake(Long id) {
        Stocktake stocktake = getLockedStocktake(id);
        requireDraft(stocktake);

        List<StocktakeDetail> details = new ArrayList<>(stocktake.getDetails());
        details.sort(Comparator.comparing(detail -> detail.getProduct().getId()));

        if (details.isEmpty()) {
            throw new IllegalStateException("Stocktake has no details");
        }

        Map<Long, Inventory> lockedInventories = new HashMap<>();

        for (StocktakeDetail detail : details) {
            Long productId = detail.getProduct().getId();

            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new IdInvalidException("Inventory not found: " + productId));

            if (inventory.getQuantityOnHand() < 0 || detail.getQuantityActual() == null || detail.getQuantityActual() < 0) {
                throw new IllegalStateException("Invalid stocktake quantity: " + productId);
            }

            if (!inventory.getQuantityOnHand().equals(detail.getQuantitySystem())) {
                throw new IllegalStateException("Inventory has changed since stocktake creation. Create a new stocktake for product: " + productId);
            }

            lockedInventories.put(productId, inventory);
        }

        for (StocktakeDetail detail : details) {
            Long productId = detail.getProduct().getId();
            Inventory inventory = lockedInventories.get(productId);

            int before = inventory.getQuantityOnHand();
            int actual = detail.getQuantityActual();
            int difference = Math.subtractExact(actual, before);

            if (difference != detail.getQuantityDifference()) {
                throw new IllegalStateException("Stocktake difference is inconsistent: " + productId);
            }

            if (difference == 0) {
                continue;
            }

            InventoryTransaction transaction = new InventoryTransaction();
            transaction.setProduct(detail.getProduct());
            transaction.setStocktakeDetail(detail);
            transaction.setTransactionType(InventoryTransactionTypeEnum.ADJUSTMENT);
            transaction.setQuantityBefore(before);
            transaction.setQuantityAfter(actual);
            transaction.setQuantityChange(difference);
            transaction.setCreatedBy(stocktake.getCreatedBy());

            inventory.setQuantityOnHand(actual);
            transactionRepository.save(transaction);
        }

        for (StocktakeDetail detail : details) {
            lowStockAlertService.synchronizeForProduct(detail.getProduct().getId());
        }

        stocktake.setStatus(StocktakeStatusEnum.COMPLETED);
        stocktakeRepository.flush();

        return toResponse(stocktake);
    }

    @Transactional
    public StocktakeDTO.Response handleCancelStocktake(Long id) {
        Stocktake stocktake = getLockedStocktake(id);
        requireDraft(stocktake);

        stocktake.setStatus(StocktakeStatusEnum.CANCELLED);
        return toResponse(stocktake);
    }

    @Transactional(readOnly = true)
    public StocktakeDTO.Response handleGetStocktakeById(Long id) {
        Stocktake stocktake = stocktakeRepository.findWithDetailsById(id)
            .orElseThrow(() -> new IdInvalidException("Stocktake not found: " + id));

        return toResponse(stocktake);
    }

    @Transactional(readOnly = true)
    public ResultPagination<List<StocktakeDTO.SummaryResponse>> handleGetAllStocktakes(int current, int pageSize, StocktakeStatusEnum status) {
        if (current < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        Pageable pageable = PageRequest.of(current - 1, pageSize, Sort.by("id").descending());
        Page<Stocktake> page = stocktakeRepository.findFiltered(status, pageable);

        List<StocktakeDTO.SummaryResponse> results = page.getContent().stream().map(this::toSummary).toList();

        Meta meta = Meta.builder().current(page.getNumber() + 1).pageSize(page.getSize()).pages(page.getTotalPages()).total(page.getTotalElements()).build();

        return ResultPagination.<List<StocktakeDTO.SummaryResponse>>builder().result(results).meta(meta).build();
    }

    @Transactional(readOnly = true)
    public List<StocktakeDTO.TransactionResponse> handleGetTransactions(Long stocktakeId) {
        if (!stocktakeRepository.existsById(stocktakeId)) {
            throw new IdInvalidException("Stocktake not found: " + stocktakeId);
        }

        return transactionRepository.findByStocktakeId(stocktakeId).stream()
            .map(transaction -> new StocktakeDTO.TransactionResponse(
                transaction.getId(),
                transaction.getProduct().getId(),
                transaction.getProduct().getProductCode(),
                transaction.getStocktakeDetail().getId(),
                transaction.getQuantityBefore(),
                transaction.getQuantityAfter(),
                transaction.getQuantityChange(),
                transaction.getCreatedAt()
            ))
            .toList();
    }

    private Stocktake getLockedStocktake(Long id) {
        return stocktakeRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IdInvalidException("Stocktake not found: " + id));
    }

    private void requireDraft(Stocktake stocktake) {
        if (stocktake.getStatus() != StocktakeStatusEnum.DRAFT) {
            throw new IllegalStateException("Only draft stocktakes can be modified or confirmed");
        }
    }

    private StocktakeDTO.Response toResponse(Stocktake stocktake) {
        List<StocktakeDTO.DetailResponse> details = stocktake.getDetails().stream()
            .sorted(Comparator.comparing(detail -> detail.getProduct().getId()))
            .map(detail -> new StocktakeDTO.DetailResponse(
                detail.getId(),
                detail.getProduct().getId(),
                detail.getProduct().getProductCode(),
                detail.getQuantitySystem(),
                detail.getQuantityActual(),
                detail.getQuantityDifference(),
                detail.getNote()
            ))
            .toList();

        return new StocktakeDTO.Response(
            stocktake.getId(),
            stocktake.getStocktakeCode(),
            stocktake.getCreatedBy(),
            stocktake.getStocktakeDate(),
            stocktake.getStatus(),
            stocktake.getNote(),
            details
        );
    }

    private StocktakeDTO.SummaryResponse toSummary(Stocktake stocktake) {
        return new StocktakeDTO.SummaryResponse(
            stocktake.getId(),
            stocktake.getStocktakeCode(),
            stocktake.getStocktakeDate(),
            stocktake.getStatus()
        );
    }
}

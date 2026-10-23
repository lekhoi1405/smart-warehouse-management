package com.lekhoi.smart_warehouse.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lekhoi.smart_warehouse.domain.dto.InventoryTransactionDTO;
import com.lekhoi.smart_warehouse.domain.dto.StockDocumentDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination.Meta;
import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.InventoryTransaction;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.domain.entity.StockDocument;
import com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail;
import com.lekhoi.smart_warehouse.domain.entity.Supplier;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.repository.InventoryTransactionRepository;
import com.lekhoi.smart_warehouse.repository.ProductRepository;
import com.lekhoi.smart_warehouse.repository.StockDocumentRepository;
import com.lekhoi.smart_warehouse.repository.SupplierRepository;
import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.SupplierStatusEnum;
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
public class StockDocumentService {

    private final StockDocumentRepository documentRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final LowStockAlertService lowStockAlertService;
    private final AuthorityService authorityService;
    @Transactional
    public StockDocumentDTO.Response handleCreateDocument(StockDocumentDTO.CreateRequest request) {
        String code = request.documentCode().trim();

        if (documentRepository.existsByDocumentCode(code)) {
            throw new AlreadyExistsException("Document code already exists");
        }

        StockDocument document = new StockDocument();
        document.setStatus(StockDocumentStatusEnum.DRAFT);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        document.setCreatedBy(authorityService.loadActive(username).id());

        applyDraftData(document, code, request.documentType(), request.supplierId(), request.documentDate(), request.note(), request.details());

        StockDocument saved = documentRepository.saveAndFlush(document);
        return toResponse(saved);
    }

    @Transactional
    public StockDocumentDTO.Response handleUpdateDraft(StockDocumentDTO.UpdateRequest request) {
        StockDocument document = getLockedDocument(request.id());
        requireStatus(document, StockDocumentStatusEnum.DRAFT);

        String code = request.documentCode().trim();

        if (documentRepository.existsByDocumentCodeAndIdNot(code, document.getId())) {
            throw new AlreadyExistsException("Document code already exists");
        }

        applyDraftData(document, code, request.documentType(), request.supplierId(), request.documentDate(), request.note(), request.details());

        documentRepository.flush();
        return toResponse(document);
    }

    @Transactional
    public StockDocumentDTO.Response handleSubmitDocument(Long id) {
        StockDocument document = getLockedDocument(id);
        requireStatus(document, StockDocumentStatusEnum.DRAFT);
        validateDocument(document);

        document.setStatus(StockDocumentStatusEnum.PENDING_CHECK);
        return toResponse(document);
    }

    @Transactional
    public StockDocumentDTO.Response handleReturnToDraft(Long id) {
        StockDocument document = getLockedDocument(id);
        requireStatus(document, StockDocumentStatusEnum.PENDING_CHECK);

        document.setStatus(StockDocumentStatusEnum.DRAFT);
        return toResponse(document);
    }

    @Transactional
    public StockDocumentDTO.Response handleConfirmDocument(Long id) {
        StockDocument document = getLockedDocument(id);
        requireStatus(document, StockDocumentStatusEnum.PENDING_CHECK);
        validateDocument(document);

        List<StockDocumentDetail> details = new ArrayList<>(document.getDetails());
        details.sort(Comparator.comparing(detail -> detail.getProduct().getId()));

        Map<Long, Inventory> lockedInventories = new HashMap<>();

        for (StockDocumentDetail detail : details) {
            Long productId = detail.getProduct().getId();

            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new IdInvalidException("Inventory not found for product: " + productId));

            lockedInventories.put(productId, inventory);
        }

        document.setStatus(StockDocumentStatusEnum.RECORDED);

        for (StockDocumentDetail detail : details) {
            Long productId = detail.getProduct().getId();
            Inventory inventory = lockedInventories.get(productId);

            int before = inventory.getQuantityOnHand();
            int quantity = detail.getQuantity();

            if (before < 0) {
                throw new IllegalStateException("Invalid inventory quantity for product: " + productId);
            }

            int change = document.getDocumentType() == StockDocumentTypeEnum.IMPORT ? quantity : -quantity;

            if (change < 0 && before < quantity) {
                throw new IllegalStateException("Insufficient stock for product: " + productId);
            }

            int after;

            try {
                after = Math.addExact(before, change);
            } catch (ArithmeticException ex) {
                throw new IllegalStateException("Inventory quantity overflow for product: " + productId);
            }

            inventory.setQuantityOnHand(after);

            InventoryTransaction transaction = new InventoryTransaction();
            transaction.setProduct(detail.getProduct());
            transaction.setDocumentDetail(detail);
            transaction.setTransactionType(document.getDocumentType() == StockDocumentTypeEnum.IMPORT
                ? InventoryTransactionTypeEnum.IMPORT : InventoryTransactionTypeEnum.EXPORT);
            transaction.setQuantityBefore(before);
            transaction.setQuantityAfter(after);
            transaction.setQuantityChange(change);
            transaction.setCreatedBy(document.getCreatedBy());

            transactionRepository.save(transaction);
        }

        for (StockDocumentDetail detail : details) {
            lowStockAlertService.synchronizeForProduct(detail.getProduct().getId());
        }

        document.setStatus(StockDocumentStatusEnum.COMPLETED);
        documentRepository.flush();

        return toResponse(document);
    }

    @Transactional
    public StockDocumentDTO.Response handleCancelDocument(Long id) {
        StockDocument document = getLockedDocument(id);

        if (document.getStatus() != StockDocumentStatusEnum.DRAFT
                && document.getStatus() != StockDocumentStatusEnum.PENDING_CHECK) {
            throw new IllegalStateException("This document cannot be cancelled");
        }

        document.setStatus(StockDocumentStatusEnum.CANCELLED);
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public StockDocumentDTO.Response handleGetDocumentById(Long id) {
        StockDocument document = documentRepository.findWithDetailsById(id)
            .orElseThrow(() -> new IdInvalidException("Document not found: " + id));

        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public ResultPagination<List<StockDocumentDTO.SummaryResponse>> handleGetAllDocuments(
            int current, int pageSize, StockDocumentTypeEnum type, StockDocumentStatusEnum status) {

        if (current < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        Pageable pageable = PageRequest.of(current - 1, pageSize, Sort.by("id").descending());
        Page<StockDocument> page = documentRepository.findFiltered(type, status, pageable);

        List<StockDocumentDTO.SummaryResponse> responses = page.getContent().stream().map(this::toSummary).toList();

        Meta meta = Meta.builder().current(page.getNumber() + 1).pageSize(page.getSize()).pages(page.getTotalPages()).total(page.getTotalElements()).build();

        return ResultPagination.<List<StockDocumentDTO.SummaryResponse>>builder().result(responses).meta(meta).build();
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionDTO.Response> handleGetTransactions(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new IdInvalidException("Document not found: " + documentId);
        }

        return transactionRepository.findByDocumentId(documentId).stream()
            .map(t -> new InventoryTransactionDTO.Response(
                t.getId(),
                t.getProduct().getId(),
                t.getProduct().getProductCode(),
                t.getDocumentDetail().getId(),
                t.getTransactionType(),
                t.getQuantityBefore(),
                t.getQuantityAfter(),
                t.getQuantityChange(),
                t.getCreatedAt()
            ))
            .toList();
    }

    private StockDocument getLockedDocument(Long id) {
        return documentRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new IdInvalidException("Document not found: " + id));
    }

    private void requireStatus(StockDocument document, StockDocumentStatusEnum expected) {
        if (document.getStatus() != expected) {
            throw new IllegalStateException("Document must be in status: " + expected);
        }
    }

    private void applyDraftData(StockDocument document, String code, StockDocumentTypeEnum type,
            Long supplierId, java.time.LocalDate date, String note, List<StockDocumentDTO.DetailRequest> requests) {

        if (type == null || date == null || code.isBlank()) {
            throw new IllegalArgumentException("Invalid document information");
        }

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Document must contain at least one detail");
        }

        Supplier supplier = null;

        if (type == StockDocumentTypeEnum.IMPORT) {
            if (supplierId == null) {
                throw new IllegalArgumentException("Supplier is required for import");
            }

            supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IdInvalidException("Supplier not found: " + supplierId));

            if (supplier.getStatus() != SupplierStatusEnum.ACTIVE) {
                throw new IllegalStateException("Supplier is not active");
            }
        } else if (supplierId != null) {
            throw new IllegalArgumentException("Export document must not have a supplier");
        }

        Set<Long> productIds = new HashSet<>();
        List<StockDocumentDetail> newDetails = new ArrayList<>();

        for (StockDocumentDTO.DetailRequest request : requests) {
            if (request.productId() == null || request.quantity() == null || request.quantity() <= 0) {
                throw new IllegalArgumentException("Invalid product or quantity");
            }

            if (!productIds.add(request.productId())) {
                throw new IllegalArgumentException("Duplicate product in document: " + request.productId());
            }

            Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IdInvalidException("Product not found: " + request.productId()));

            if (product.getStatus() != ProductStatusEnum.ACTIVE) {
                throw new IllegalStateException("Product is not active: " + product.getId());
            }

            StockDocumentDetail detail = new StockDocumentDetail();
            detail.setDocument(document);
            detail.setProduct(product);
            detail.setQuantity(request.quantity());
            newDetails.add(detail);
        }

        document.setDocumentCode(code);
        document.setDocumentType(type);
        document.setSupplier(supplier);
        document.setDocumentDate(date);
        document.setNote(note);
        document.getDetails().clear();
        document.getDetails().addAll(newDetails);
    }

    private void validateDocument(StockDocument document) {
        if (document.getDetails().isEmpty()) {
            throw new IllegalStateException("Document has no details");
        }

        if (document.getDocumentType() == StockDocumentTypeEnum.IMPORT) {
            if (document.getSupplier() == null || document.getSupplier().getStatus() != SupplierStatusEnum.ACTIVE) {
                throw new IllegalStateException("Supplier is not active or missing");
            }
        } else if (document.getSupplier() != null) {
            throw new IllegalStateException("Export document must not have a supplier");
        }

        Set<Long> productIds = new HashSet<>();

        for (StockDocumentDetail detail : document.getDetails()) {
            Product product = detail.getProduct();

            if (!productIds.add(product.getId())) {
                throw new IllegalStateException("Duplicate product in document: " + product.getId());
            }

            if (product.getStatus() != ProductStatusEnum.ACTIVE) {
                throw new IllegalStateException("Product is not active: " + product.getId());
            }

            if (detail.getQuantity() == null || detail.getQuantity() <= 0) {
                throw new IllegalStateException("Invalid document quantity");
            }
        }
    }

    private StockDocumentDTO.Response toResponse(StockDocument document) {
        List<StockDocumentDTO.DetailResponse> details = document.getDetails().stream()
            .map(detail -> new StockDocumentDTO.DetailResponse(
                detail.getId(),
                detail.getProduct().getId(),
                detail.getProduct().getProductCode(),
                detail.getQuantity()
            ))
            .toList();

        return new StockDocumentDTO.Response(
            document.getId(),
            document.getDocumentCode(),
            document.getDocumentType(),
            document.getSupplier() == null ? null : document.getSupplier().getId(),
            document.getCreatedBy(),
            document.getDocumentDate(),
            document.getStatus(),
            document.getNote(),
            details
        );
    }

    private StockDocumentDTO.SummaryResponse toSummary(StockDocument document) {
        return new StockDocumentDTO.SummaryResponse(
            document.getId(),
            document.getDocumentCode(),
            document.getDocumentType(),
            document.getSupplier() == null ? null : document.getSupplier().getId(),
            document.getDocumentDate(),
            document.getStatus()
        );
    }
}

package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.lekhoi.smart_warehouse.domain.dto.StockDocumentDTO;
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
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.SupplierStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

@ExtendWith(MockitoExtension.class)
class StockDocumentServiceTest {

    @Mock private StockDocumentRepository documentRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private LowStockAlertService lowStockAlertService;
    @Mock private AuthorityService authorityService;
    @InjectMocks private StockDocumentService stockDocumentService;

    private Product activeProduct() {
        Product product = Product.builder().productCode("P-1").productName("Rice").unit("bag")
            .status(ProductStatusEnum.ACTIVE).minStockThreshold(3).build();
        product.setId(1L);
        return product;
    }

    private Supplier supplier(SupplierStatusEnum status) {
        Supplier supplier = Supplier.builder().supplierCode("S-1").supplierName("Supplier")
            .status(status).build();
        supplier.setId(2L);
        return supplier;
    }

    private StockDocumentDTO.DetailRequest detail(Long productId, Integer quantity) {
        return new StockDocumentDTO.DetailRequest(productId, quantity);
    }

    private StockDocumentDTO.CreateRequest createRequest(StockDocumentTypeEnum type, Long supplierId,
            List<StockDocumentDTO.DetailRequest> details) {
        return new StockDocumentDTO.CreateRequest(" DOC-1 ", type, supplierId,
            LocalDate.of(2025, 1, 5), "note", details);
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("operator", "password"));
        when(authorityService.loadActive("operator"))
            .thenReturn(new AuthorityService.Snapshot(7L, "operator", "Operator", "encoded", null, java.util.Set.of(), java.util.Set.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Product product(long id, ProductStatusEnum status, int threshold) {
        Product product = Product.builder().productCode("P-" + id).productName("Product " + id)
            .unit("unit").status(status).minStockThreshold(threshold).build();
        product.setId(id);
        return product;
    }

    private Inventory inventory(Product product, int quantity) {
        return Inventory.builder().product(product).quantityOnHand(quantity).build();
    }

    private StockDocument pendingDocument(StockDocumentTypeEnum type, SupplierStatusEnum supplierStatus,
            Product... products) {
        StockDocument document = new StockDocument();
        document.setId(1L);
        document.setDocumentCode("DOC-1");
        document.setDocumentType(type);
        document.setDocumentDate(LocalDate.of(2025, 1, 1));
        document.setCreatedBy(7L);
        document.setStatus(StockDocumentStatusEnum.PENDING_CHECK);
        if (supplierStatus != null) {
            Supplier supplier = new Supplier();
            supplier.setId(5L);
            supplier.setSupplierCode("SUP-1");
            supplier.setStatus(supplierStatus);
            document.setSupplier(supplier);
        }
        for (Product item : products) {
            StockDocumentDetail detail = new StockDocumentDetail();
            detail.setDocument(document);
            detail.setProduct(item);
            detail.setQuantity(2);
            document.getDetails().add(detail);
        }
        return document;
    }

    private void stubPendingDocument(StockDocument document) {
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(Optional.of(document));
    }

    // FUNCTION 1: StockDocumentService.handleConfirmDocument
    @Test
    void confirmDocument_import_updatesInventoryTransactionAndAlert() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 5);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.IMPORT, SupplierStatusEnum.ACTIVE, p);
        document.getDetails().get(0).setQuantity(5);
        Inventory stock = inventory(p, 10);
        stubPendingDocument(document);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(stock));

        var response = stockDocumentService.handleConfirmDocument(1L);

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(captor.capture());
        InventoryTransaction transaction = captor.getValue();
        assertEquals(15, stock.getQuantityOnHand());
        assertEquals(InventoryTransactionTypeEnum.IMPORT, transaction.getTransactionType());
        assertEquals(10, transaction.getQuantityBefore());
        assertEquals(15, transaction.getQuantityAfter());
        assertEquals(5, transaction.getQuantityChange());
        assertEquals(StockDocumentStatusEnum.COMPLETED, document.getStatus());
        assertEquals(StockDocumentStatusEnum.COMPLETED, response.status());
        verify(lowStockAlertService).synchronizeForProduct(1L);
    }

    @Test
    void confirmDocument_export_updatesInventoryAndRecordsNegativeChange() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 5);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null, p);
        document.getDetails().get(0).setQuantity(4);
        Inventory stock = inventory(p, 10);
        stubPendingDocument(document);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(stock));

        stockDocumentService.handleConfirmDocument(1L);

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(6, stock.getQuantityOnHand());
        assertEquals(InventoryTransactionTypeEnum.EXPORT, captor.getValue().getTransactionType());
        assertEquals(-4, captor.getValue().getQuantityChange());
    }

    @Test
    void confirmDocument_exportExactlyAllStockLeavesZero() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 0);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null, p);
        document.getDetails().get(0).setQuantity(10);
        Inventory stock = inventory(p, 10);
        stubPendingDocument(document);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(stock));
        stockDocumentService.handleConfirmDocument(1L);
        assertEquals(0, stock.getQuantityOnHand());
    }

    @Test
    void confirmDocument_missingDocumentDoesNotReadInventoryOrSaveTransaction() {
        when(documentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> stockDocumentService.handleConfirmDocument(99L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmDocument_wrongStatusIsRejected() {
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null, product(1, ProductStatusEnum.ACTIVE, 1));
        document.setStatus(StockDocumentStatusEnum.DRAFT);
        stubPendingDocument(document);
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmDocument_emptyDetailsIsRejected() {
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null);
        stubPendingDocument(document);
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmDocument_missingInventoryIsRejectedBeforeSavingTransaction() {
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null, product(1, ProductStatusEnum.ACTIVE, 1));
        stubPendingDocument(document);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmDocument_negativeCurrentInventoryIsRejected() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 1);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.IMPORT, SupplierStatusEnum.ACTIVE, p);
        stubPendingDocument(document);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(p, -1)));
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmDocument_exportGreaterThanStockIsRejected() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 1);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null, p);
        document.getDetails().get(0).setQuantity(5);
        stubPendingDocument(document);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(p, 4)));
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmDocument_importOverflowIsRejected() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 1);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.IMPORT, SupplierStatusEnum.ACTIVE, p);
        document.getDetails().get(0).setQuantity(1);
        stubPendingDocument(document);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(p, Integer.MAX_VALUE)));
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmDocument_duplicateProductIsRejected() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 1);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null, p, p);
        stubPendingDocument(document);
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmDocument_importWithInactiveSupplierIsRejected() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 1);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.IMPORT, SupplierStatusEnum.INACTIVE, p);
        stubPendingDocument(document);
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmDocument_inactiveProductIsRejected() {
        Product p = product(1, ProductStatusEnum.INACTIVE, 1);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null, p);
        stubPendingDocument(document);
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmDocument_zeroQuantityIsRejected() {
        Product p = product(1, ProductStatusEnum.ACTIVE, 1);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.EXPORT, null, p);
        document.getDetails().get(0).setQuantity(0);
        stubPendingDocument(document);
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleConfirmDocument(1L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmDocument_multipleValidProductsUpdatesBothAndSynchronizesAlerts() {
        Product first = product(1, ProductStatusEnum.ACTIVE, 1);
        Product second = product(2, ProductStatusEnum.ACTIVE, 1);
        StockDocument document = pendingDocument(StockDocumentTypeEnum.IMPORT, SupplierStatusEnum.ACTIVE, first, second);
        stubPendingDocument(document);
        Inventory stock1 = inventory(first, 3);
        Inventory stock2 = inventory(second, 7);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(stock1));
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(stock2));

        stockDocumentService.handleConfirmDocument(1L);

        assertEquals(5, stock1.getQuantityOnHand());
        assertEquals(9, stock2.getQuantityOnHand());
        verify(transactionRepository, times(2)).save(any(InventoryTransaction.class));
        verify(lowStockAlertService).synchronizeForProduct(1L);
        verify(lowStockAlertService).synchronizeForProduct(2L);
    }

    // FUNCTION 18: StockDocumentService.handleCreateDocument
    @Test
    void createDocument_importCreatesDraftWithSupplierAndDetails() {
        authenticate(); Product product = activeProduct(); Supplier supplier = supplier(SupplierStatusEnum.ACTIVE);
        when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(false);
        when(supplierRepository.findById(2L)).thenReturn(Optional.of(supplier));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(documentRepository.saveAndFlush(any(StockDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.IMPORT, 2L, List.of(detail(1L, 5))));

        ArgumentCaptor<StockDocument> savedCaptor = ArgumentCaptor.forClass(StockDocument.class);
        verify(documentRepository, times(1)).saveAndFlush(savedCaptor.capture());
        StockDocument saved = savedCaptor.getValue();
        assertEquals("DOC-1", saved.getDocumentCode());
        assertEquals(StockDocumentStatusEnum.DRAFT, saved.getStatus());
        assertEquals(7L, saved.getCreatedBy());
        assertSame(supplier, saved.getSupplier());
        assertEquals(1, saved.getDetails().size());
        assertSame(product, saved.getDetails().get(0).getProduct());
        assertSame(saved, saved.getDetails().get(0).getDocument());
        assertEquals(5, saved.getDetails().get(0).getQuantity());
        assertEquals("DOC-1", response.documentCode());
        assertEquals(StockDocumentStatusEnum.DRAFT, response.status());
        assertEquals(7L, response.createdBy());
        assertEquals(2L, response.supplierId());
        assertEquals(1, response.details().size());
        assertEquals(5, response.details().get(0).quantity());
    }

    @Test
    void createDocument_exportWithoutSupplierIsAllowed() {
        authenticate(); Product product = activeProduct();
        when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(documentRepository.saveAndFlush(any(StockDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.EXPORT, null, List.of(detail(1L, 2))));

        ArgumentCaptor<StockDocument> savedCaptor = ArgumentCaptor.forClass(StockDocument.class);
        verify(documentRepository, times(1)).saveAndFlush(savedCaptor.capture());
        StockDocument saved = savedCaptor.getValue();
        assertEquals(StockDocumentTypeEnum.EXPORT, saved.getDocumentType());
        assertNull(saved.getSupplier());
        assertEquals(StockDocumentStatusEnum.DRAFT, saved.getStatus());
        assertEquals(7L, saved.getCreatedBy());
        assertEquals(1, saved.getDetails().size());
        assertEquals(2, saved.getDetails().get(0).getQuantity());

        assertEquals(StockDocumentTypeEnum.EXPORT, response.documentType()); assertNull(response.supplierId());
        assertEquals(StockDocumentStatusEnum.DRAFT, response.status());
    }

    @Test
    void createDocument_duplicateCodeRejectedBeforeSave() {
        when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(true);
        assertThrows(AlreadyExistsException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.EXPORT, null, List.of(detail(1L, 1)))));
        verify(documentRepository, never()).saveAndFlush(any()); verifyNoInteractions(productRepository, supplierRepository);
    }

    @Test
    void createDocument_importRequiresSupplier() {
        authenticate(); when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.IMPORT, null, List.of(detail(1L, 1)))));
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDocument_missingOrInactiveSupplierRejected() {
        authenticate(); when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(false);
        when(supplierRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.IMPORT, 2L, List.of(detail(1L, 1)))));
        when(supplierRepository.findById(2L)).thenReturn(Optional.of(supplier(SupplierStatusEnum.INACTIVE)));
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.IMPORT, 2L, List.of(detail(1L, 1)))));
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDocument_exportMustNotHaveSupplier() {
        authenticate(); when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.EXPORT, 2L, List.of(detail(1L, 1)))));
        verifyNoInteractions(supplierRepository);
    }

    @Test
    void createDocument_rejectsEmptyDetailsAndInvalidQuantity() {
        authenticate(); when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.EXPORT, null, List.of())));
        assertThrows(IllegalArgumentException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.EXPORT, null, List.of(detail(1L, 0)))));
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDocument_rejectsMissingOrInactiveProduct() {
        authenticate(); when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(false);
        when(productRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.EXPORT, null, List.of(detail(1L, 1)))));
        Product inactive = activeProduct(); inactive.setStatus(ProductStatusEnum.INACTIVE);
        when(productRepository.findById(1L)).thenReturn(Optional.of(inactive));
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.EXPORT, null, List.of(detail(1L, 1)))));
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDocument_duplicateProductRejected() {
        authenticate(); when(documentRepository.existsByDocumentCode("DOC-1")).thenReturn(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct()));
        assertThrows(IllegalArgumentException.class, () -> stockDocumentService.handleCreateDocument(createRequest(StockDocumentTypeEnum.EXPORT, null, List.of(detail(1L, 1), detail(1L, 2)))));
        verify(documentRepository, never()).saveAndFlush(any());
    }

    private StockDocument existingDraft() {
        StockDocument document = new StockDocument(); document.setId(10L); document.setDocumentCode("OLD");
        document.setDocumentType(StockDocumentTypeEnum.EXPORT); document.setDocumentDate(LocalDate.of(2025, 1, 1));
        document.setStatus(StockDocumentStatusEnum.DRAFT); document.setDetails(new java.util.ArrayList<>());
        return document;
    }

    private void stubExisting(StockDocument document) {
        when(documentRepository.findByIdForUpdate(document.getId())).thenReturn(Optional.of(document));
    }

    private StockDocumentDTO.UpdateRequest updateRequest(Long id, String code, List<StockDocumentDTO.DetailRequest> details) {
        return new StockDocumentDTO.UpdateRequest(id, code, StockDocumentTypeEnum.EXPORT, null, LocalDate.of(2025, 2, 1), "updated", details);
    }

    // FUNCTION 19: StockDocumentService.handleUpdateDraft
    @Test
    void updateDraft_updatesDraftAndReplacesDetails() {
        StockDocument document = existingDraft(); stubExisting(document); Product product = activeProduct();
        when(documentRepository.existsByDocumentCodeAndIdNot("NEW", 10L)).thenReturn(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        var response = stockDocumentService.handleUpdateDraft(updateRequest(10L, " NEW ", List.of(detail(1L, 4))));
        assertEquals("NEW", document.getDocumentCode()); assertEquals(StockDocumentStatusEnum.DRAFT, response.status());
        assertEquals(1, document.getDetails().size()); assertEquals(4, document.getDetails().get(0).getQuantity());
        verify(documentRepository).flush();
    }

    @Test
    void updateDraft_missingDocumentThrows() {
        when(documentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> stockDocumentService.handleUpdateDraft(updateRequest(99L, "NEW", List.of(detail(1L, 1)))));
    }

    @Test
    void updateDraft_nonDraftDocumentThrows() {
        StockDocument document = existingDraft(); document.setStatus(StockDocumentStatusEnum.COMPLETED); stubExisting(document);
        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleUpdateDraft(updateRequest(10L, "NEW", List.of(detail(1L, 1)))));
    }

    @Test
    void updateDraft_duplicateCodeThrows() {
        StockDocument document = existingDraft(); stubExisting(document);
        when(documentRepository.existsByDocumentCodeAndIdNot("NEW", 10L)).thenReturn(true);
        assertThrows(AlreadyExistsException.class, () -> stockDocumentService.handleUpdateDraft(updateRequest(10L, "NEW", List.of(detail(1L, 1)))));
        verify(documentRepository, never()).flush();
    }

    @Test
    void updateDraft_emptyDetailsRejectedWithoutMutation() {
        StockDocument document = existingDraft(); stubExisting(document);

        assertThrows(IllegalArgumentException.class, () -> stockDocumentService.handleUpdateDraft(updateRequest(10L, "NEW", List.of())));

        assertEquals("OLD", document.getDocumentCode());
        assertTrue(document.getDetails().isEmpty());
        verify(documentRepository, never()).flush();
    }

    @Test
    void updateDraft_nonPositiveDetailQuantityRejectedWithoutFlush() {
        StockDocument document = existingDraft(); stubExisting(document);

        assertThrows(IllegalArgumentException.class,
            () -> stockDocumentService.handleUpdateDraft(updateRequest(10L, "NEW", List.of(detail(1L, 0)))));

        assertEquals("OLD", document.getDocumentCode());
        assertTrue(document.getDetails().isEmpty());
        verify(documentRepository, never()).flush();
    }

    @Test
    void updateDraft_inactiveSupplierRejectedWithoutFlush() {
        StockDocument document = existingDraft(); stubExisting(document);
        when(documentRepository.existsByDocumentCodeAndIdNot("NEW", 10L)).thenReturn(false);
        when(supplierRepository.findById(2L)).thenReturn(Optional.of(supplier(SupplierStatusEnum.INACTIVE)));
        StockDocumentDTO.UpdateRequest request = new StockDocumentDTO.UpdateRequest(
            10L, "NEW", StockDocumentTypeEnum.IMPORT, 2L,
            LocalDate.of(2025, 2, 1), "updated", List.of(detail(1L, 1)));

        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleUpdateDraft(request));

        assertEquals("OLD", document.getDocumentCode());
        assertTrue(document.getDetails().isEmpty());
        verify(documentRepository, never()).flush();
        verifyNoInteractions(productRepository);
    }

    // FUNCTION 20: StockDocumentService.handleSubmitDocument
    @Test
    void submitDocument_validDraftMovesToPendingCheck() {
        StockDocument document = existingDraft(); Product product = activeProduct();
        com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail detail = new com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail();
        detail.setDocument(document); detail.setProduct(product); detail.setQuantity(3); document.getDetails().add(detail); stubExisting(document);
        var response = stockDocumentService.handleSubmitDocument(10L);
        assertEquals(StockDocumentStatusEnum.PENDING_CHECK, document.getStatus());
        assertEquals(StockDocumentStatusEnum.PENDING_CHECK, response.status());
    }

    @Test
    void submitDocument_missingDocumentThrows() {
        when(documentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(IdInvalidException.class, () -> stockDocumentService.handleSubmitDocument(99L));

        verify(documentRepository).findByIdForUpdate(99L);
    }

    @Test
    void submitDocument_wrongStatusThrowsWithoutChangingStatus() {
        StockDocument document = existingDraft();
        document.setStatus(StockDocumentStatusEnum.COMPLETED);
        stubExisting(document);

        assertThrows(IllegalStateException.class, () -> stockDocumentService.handleSubmitDocument(10L));

        assertEquals(StockDocumentStatusEnum.COMPLETED, document.getStatus());
    }

    @Test
    void submitDocument_invalidDetailsSupplierProductOrQuantityRejectedWithoutStatusChange() {
        StockDocument emptyDocument = existingDraft();
        StockDocument inactiveSupplierDocument = existingDraft();
        inactiveSupplierDocument.setId(11L);
        inactiveSupplierDocument.setDocumentType(StockDocumentTypeEnum.IMPORT);
        inactiveSupplierDocument.setSupplier(supplier(SupplierStatusEnum.INACTIVE));
        StockDocument inactiveProductDocument = existingDraft();
        inactiveProductDocument.setId(12L);
        StockDocument invalidQuantityDocument = existingDraft();
        invalidQuantityDocument.setId(13L);

        Product active = activeProduct();
        Product inactive = activeProduct();
        inactive.setStatus(ProductStatusEnum.INACTIVE);

        com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail inactiveSupplierDetail =
            new com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail();
        inactiveSupplierDetail.setDocument(inactiveSupplierDocument);
        inactiveSupplierDetail.setProduct(active);
        inactiveSupplierDetail.setQuantity(2);
        inactiveSupplierDocument.getDetails().add(inactiveSupplierDetail);

        com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail inactiveProductDetail =
            new com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail();
        inactiveProductDetail.setDocument(inactiveProductDocument);
        inactiveProductDetail.setProduct(inactive);
        inactiveProductDetail.setQuantity(2);
        inactiveProductDocument.getDetails().add(inactiveProductDetail);

        com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail invalidQuantityDetail =
            new com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail();
        invalidQuantityDetail.setDocument(invalidQuantityDocument);
        invalidQuantityDetail.setProduct(active);
        invalidQuantityDetail.setQuantity(0);
        invalidQuantityDocument.getDetails().add(invalidQuantityDetail);

        when(documentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(emptyDocument));
        when(documentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(inactiveSupplierDocument));
        when(documentRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(inactiveProductDocument));
        when(documentRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(invalidQuantityDocument));

        assertAll(
            () -> assertThrows(IllegalStateException.class, () -> stockDocumentService.handleSubmitDocument(10L)),
            () -> assertThrows(IllegalStateException.class, () -> stockDocumentService.handleSubmitDocument(11L)),
            () -> assertThrows(IllegalStateException.class, () -> stockDocumentService.handleSubmitDocument(12L)),
            () -> assertThrows(IllegalStateException.class, () -> stockDocumentService.handleSubmitDocument(13L))
        );

        assertEquals(StockDocumentStatusEnum.DRAFT, emptyDocument.getStatus());
        assertEquals(StockDocumentStatusEnum.DRAFT, inactiveSupplierDocument.getStatus());
        assertEquals(StockDocumentStatusEnum.DRAFT, inactiveProductDocument.getStatus());
        assertEquals(StockDocumentStatusEnum.DRAFT, invalidQuantityDocument.getStatus());
    }
}

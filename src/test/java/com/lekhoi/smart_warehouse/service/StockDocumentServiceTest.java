package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
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

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StockDocumentServiceTest {

    @Mock
    private StockDocumentRepository documentRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @InjectMocks
    private StockDocumentService stockDocumentService;

    private StockDocument mockDocument;

    @BeforeEach
    void setUp() {
        mockDocument = new StockDocument();
        mockDocument.setId(1L);
    }

    // FUNCTION 21: handleReturnToDraft

    @Test
    void testHandleReturnToDraft_Success() {
        mockDocument.setStatus(StockDocumentStatusEnum.PENDING_CHECK);
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockDocument));

        StockDocumentDTO.Response response = stockDocumentService.handleReturnToDraft(1L);

        assertEquals(StockDocumentStatusEnum.DRAFT, mockDocument.getStatus());
        assertNotNull(response);
    }

    @Test
    void testHandleReturnToDraft_WrongStatus_ThrowsException() {
        mockDocument.setStatus(StockDocumentStatusEnum.COMPLETED);
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockDocument));

        assertThrows(RuntimeException.class, () -> {
            stockDocumentService.handleReturnToDraft(1L);
        });
        assertEquals(StockDocumentStatusEnum.COMPLETED, mockDocument.getStatus());
    }

    @Test
    void testHandleReturnToDraft_NotFound_ThrowsException() {
        when(documentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            stockDocumentService.handleReturnToDraft(99L);
        });
    }

    // FUNCTION 22: handleCancelDocument

    @Test
    void testHandleCancelDocument_DraftStatus_Success() {
        mockDocument.setStatus(StockDocumentStatusEnum.DRAFT);
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockDocument));

        StockDocumentDTO.Response response = stockDocumentService.handleCancelDocument(1L);

        assertEquals(StockDocumentStatusEnum.CANCELLED, mockDocument.getStatus());
        assertNotNull(response);
    }

    @Test
    void testHandleCancelDocument_PendingCheckStatus_Success() {
        mockDocument.setStatus(StockDocumentStatusEnum.PENDING_CHECK);
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockDocument));

        StockDocumentDTO.Response response = stockDocumentService.handleCancelDocument(1L);

        assertEquals(StockDocumentStatusEnum.CANCELLED, mockDocument.getStatus());
        assertNotNull(response);
    }

    @Test
    void testHandleCancelDocument_InvalidStatus_ThrowsException() {
        mockDocument.setStatus(StockDocumentStatusEnum.COMPLETED);
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockDocument));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            stockDocumentService.handleCancelDocument(1L);
        });
        assertEquals("This document cannot be cancelled", exception.getMessage());
        assertEquals(StockDocumentStatusEnum.COMPLETED, mockDocument.getStatus());
    }

    @Test
    void testHandleCancelDocument_NotFound_ThrowsException() {
        when(documentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            stockDocumentService.handleCancelDocument(99L);
        });
    }

    // FUNCTION 23: handleGetDocumentById

    @Test
    void testHandleGetDocumentById_Success() {
        when(documentRepository.findWithDetailsById(1L)).thenReturn(Optional.of(mockDocument));
        
        StockDocumentDTO.Response response = stockDocumentService.handleGetDocumentById(1L);
        
        assertNotNull(response);
    }

    @Test
    void testHandleGetDocumentById_NotFound_ThrowsException() {
        when(documentRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());
        
        assertThrows(RuntimeException.class, () -> {
            stockDocumentService.handleGetDocumentById(99L);
        });
    }

    // FUNCTION 24: handleGetAllDocuments

    @Test
    void testHandleGetAllDocuments_InvalidPagination_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            stockDocumentService.handleGetAllDocuments(0, 10, null, null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            stockDocumentService.handleGetAllDocuments(1, 0, null, null);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            stockDocumentService.handleGetAllDocuments(1, 101, null, null);
        });
    }

    @Test
    void testHandleGetAllDocuments_Success() {
        org.springframework.data.domain.Page<StockDocument> page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(mockDocument));
        when(documentRepository.findFiltered(any(), any(), any())).thenReturn(page);
        
        var result = stockDocumentService.handleGetAllDocuments(1, 10, null, null);
            
        assertNotNull(result);
        assertEquals(1, result.getMeta().getTotal());
    }

    // FUNCTION 25: handleGetTransactions

    @Test
    void testHandleGetTransactions_NotFound_ThrowsException() {
        when(documentRepository.existsById(99L)).thenReturn(false);
        
        assertThrows(RuntimeException.class, () -> {
            stockDocumentService.handleGetTransactions(99L);
        });
    }

    @Test
    void testHandleGetTransactions_Success() {
        when(documentRepository.existsById(1L)).thenReturn(true);
        when(transactionRepository.findByDocumentId(1L)).thenReturn(java.util.List.of());
        
        var result = stockDocumentService.handleGetTransactions(1L);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}

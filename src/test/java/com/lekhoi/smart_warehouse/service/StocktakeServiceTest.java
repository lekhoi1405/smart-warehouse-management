package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

import com.lekhoi.smart_warehouse.domain.dto.StocktakeDTO;
import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.domain.entity.Stocktake;
import com.lekhoi.smart_warehouse.domain.entity.StocktakeDetail;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.repository.StocktakeRepository;
import com.lekhoi.smart_warehouse.util.constant.StocktakeStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

@ExtendWith(MockitoExtension.class)
public class StocktakeServiceTest {

    @Mock
    private StocktakeRepository stocktakeRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private AuthorityService authorityService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private StocktakeService stocktakeService;

    private Stocktake mockStocktake;

    @BeforeEach
    void setUp() {
        mockStocktake = new Stocktake();
        mockStocktake.setId(1L);
        mockStocktake.setStatus(StocktakeStatusEnum.DRAFT);
        mockStocktake.setDetails(new ArrayList<>());
    }

    // FUNCTION 26: handleCreateStocktake

    @Test
    void testHandleCreateStocktake_InvalidInfo_ThrowsException() {
        StocktakeDTO.CreateRequest request = new StocktakeDTO.CreateRequest("", null, null, null);

        assertThrows(IllegalArgumentException.class, () -> {
            stocktakeService.handleCreateStocktake(request);
        });
    }

    @Test
    void testHandleCreateStocktake_CodeExists_ThrowsException() {
        StocktakeDTO.CreateRequest request = new StocktakeDTO.CreateRequest("ST01", java.time.LocalDate.now(), "Note", List.of(new StocktakeDTO.DetailRequest(1L, 10, "Note")));
        when(stocktakeRepository.existsByStocktakeCode("ST01")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> {
            stocktakeService.handleCreateStocktake(request);
        });
    }

    // FUNCTION 27: handleUpdateDraft

    @Test
    void testHandleUpdateDraft_InvalidDetailsSize_ThrowsException() {
        mockStocktake.getDetails().add(new StocktakeDetail());
        when(stocktakeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockStocktake));

        StocktakeDTO.UpdateRequest request = new StocktakeDTO.UpdateRequest(1L, List.of());

        assertThrows(IllegalArgumentException.class, () -> {
            stocktakeService.handleUpdateDraft(request);
        });
    }

    @Test
    void testHandleUpdateDraft_DuplicateProduct_ThrowsException() {
        Product p = new Product(); p.setId(1L);
        StocktakeDetail detail = new StocktakeDetail(); detail.setProduct(p); detail.setQuantitySystem(5);
        mockStocktake.getDetails().add(detail);
        
        when(stocktakeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockStocktake));

        StocktakeDTO.UpdateRequest request = new StocktakeDTO.UpdateRequest(1L, List.of(
            new StocktakeDTO.CountUpdateRequest(1L, 10, ""),
            new StocktakeDTO.CountUpdateRequest(1L, 12, "")
        ));

        assertThrows(IllegalArgumentException.class, () -> {
            stocktakeService.handleUpdateDraft(request);
        });
    }

    // FUNCTION 28: handleCancelStocktake

    @Test
    void testHandleCancelStocktake_Success() {
        when(stocktakeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockStocktake));

        StocktakeDTO.Response response = stocktakeService.handleCancelStocktake(1L);

        assertEquals(StocktakeStatusEnum.CANCELLED, mockStocktake.getStatus());
        assertNotNull(response);
    }

    @Test
    void testHandleCancelStocktake_WrongStatus_ThrowsException() {
        mockStocktake.setStatus(StocktakeStatusEnum.COMPLETED);
        when(stocktakeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mockStocktake));

        assertThrows(RuntimeException.class, () -> {
            stocktakeService.handleCancelStocktake(1L);
        });
    }

    // FUNCTION 29: handleGetStocktakeById

    @Test
    void testHandleGetStocktakeById_Success() {
        when(stocktakeRepository.findWithDetailsById(1L)).thenReturn(Optional.of(mockStocktake));

        StocktakeDTO.Response response = stocktakeService.handleGetStocktakeById(1L);

        assertNotNull(response);
    }

    @Test
    void testHandleGetStocktakeById_NotFound_ThrowsException() {
        when(stocktakeRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            stocktakeService.handleGetStocktakeById(99L);
        });
    }

    // FUNCTION 30: handleGetAllStocktakes

    @Test
    void testHandleGetAllStocktakes_InvalidPagination_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            stocktakeService.handleGetAllStocktakes(0, 10, null);
        });
    }

    @Test
    void testHandleGetAllStocktakes_Success() {
        Page<Stocktake> page = new PageImpl<>(List.of(mockStocktake));
        when(stocktakeRepository.findFiltered(any(), any())).thenReturn(page);

        var result = stocktakeService.handleGetAllStocktakes(1, 10, null);

        assertNotNull(result);
        assertEquals(1, result.getMeta().getTotal());
    }
}

package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockLevelEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @InjectMocks private InventoryService inventoryService;

    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        product = Product.builder().productCode("P-01").productName("Rice").unit("bag")
            .status(ProductStatusEnum.ACTIVE).minStockThreshold(5).build();
        product.setId(1L);
        inventory = Inventory.builder().product(product).quantityOnHand(3).build();
        inventory.setId(10L);
    }

    // FUNCTION 15: initializeForProduct
    @Test
    void initializeForProduct_savesZeroQuantityInventory() {
        inventoryService.initializeForProduct(product);
        verify(inventoryRepository).save(argThat(saved -> saved.getProduct() == product && saved.getQuantityOnHand() == 0));
    }

    // FUNCTION 16: handleGetInventoryByProductId
    @Test
    void getInventoryByProductId_mapsLowStockInventory() {
        when(inventoryRepository.findWithProductByProductId(1L)).thenReturn(Optional.of(inventory));

        var result = inventoryService.handleGetInventoryByProductId(1L);

        assertEquals(1L, result.productId());
        assertEquals(3, result.quantityOnHand());
        assertEquals(StockLevelEnum.LOW, result.stockLevel());
    }

    @Test
    void getInventoryByProductId_mapsOutOfStockAndNormalLevels() {
        when(inventoryRepository.findWithProductByProductId(1L)).thenReturn(Optional.of(inventory));
        inventory.setQuantityOnHand(0);
        assertEquals(StockLevelEnum.OUT_OF_STOCK, inventoryService.handleGetInventoryByProductId(1L).stockLevel());

        inventory.setQuantityOnHand(6);
        assertEquals(StockLevelEnum.NORMAL, inventoryService.handleGetInventoryByProductId(1L).stockLevel());
    }

    @Test
    void getInventoryByProductId_missingInventoryThrows() {
        when(inventoryRepository.findWithProductByProductId(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> inventoryService.handleGetInventoryByProductId(99L));
    }

    // FUNCTION 17: handleSearchInventories
    @Test
    void searchInventories_rejectsInvalidPagination() {
        assertThrows(IllegalArgumentException.class, () -> inventoryService.handleSearchInventories(0, 10, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.handleSearchInventories(1, 0, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.handleSearchInventories(1, 101, null, null, null, null));
    }

    @Test
    void searchInventories_trimsFiltersAndMapsResults() {
        when(inventoryRepository.searchInventories(eq("rice"), eq("P-01"), eq("Food"), eq(ProductStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(inventory)));

        var result = inventoryService.handleSearchInventories(1, 10, " rice ", " P-01 ", " Food ", ProductStatusEnum.ACTIVE);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals("P-01", result.getResult().get(0).productCode());
        assertEquals(StockLevelEnum.LOW, result.getResult().get(0).stockLevel());
    }

    @Test
    void searchInventories_blankFiltersBecomeNullAndKeywordDefaultsEmpty() {
        when(inventoryRepository.searchInventories(eq(""), isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        var result = inventoryService.handleSearchInventories(1, 10, null, "  ", " ", null);

        assertTrue(result.getResult().isEmpty());
        assertEquals(0, result.getMeta().getTotal());
    }
}

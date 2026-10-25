package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.InventoryTransaction;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.domain.entity.Stocktake;
import com.lekhoi.smart_warehouse.domain.entity.StocktakeDetail;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.repository.InventoryTransactionRepository;
import com.lekhoi.smart_warehouse.repository.StocktakeRepository;
import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StocktakeStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

@ExtendWith(MockitoExtension.class)
class StocktakeServiceTest {

    @Mock private StocktakeRepository stocktakeRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private LowStockAlertService lowStockAlertService;
    @Mock private AuthorityService authorityService;
    @InjectMocks private StocktakeService stocktakeService;

    private Product product(long id) {
        Product product = Product.builder().productCode("P-" + id).productName("Product " + id)
            .unit("unit").status(ProductStatusEnum.ACTIVE).minStockThreshold(2).build();
        product.setId(id);
        return product;
    }

    private Inventory inventory(Product product, int quantity) {
        return Inventory.builder().product(product).quantityOnHand(quantity).build();
    }

    private Stocktake stocktake(int actual, int snapshot) {
        Stocktake stocktake = new Stocktake();
        stocktake.setId(2L);
        stocktake.setStocktakeCode("ST-2");
        stocktake.setStocktakeDate(LocalDate.of(2025, 1, 1));
        stocktake.setCreatedBy(8L);
        stocktake.setStatus(StocktakeStatusEnum.DRAFT);
        Product product = product(1);
        StocktakeDetail detail = new StocktakeDetail();
        detail.setId(20L);
        detail.setStocktake(stocktake);
        detail.setProduct(product);
        detail.setQuantitySystem(snapshot);
        detail.setQuantityActual(actual);
        detail.setQuantityDifference(actual - snapshot);
        stocktake.getDetails().add(detail);
        return stocktake;
    }

    private void stubStocktake(Stocktake stocktake) {
        when(stocktakeRepository.findByIdForUpdate(stocktake.getId())).thenReturn(Optional.of(stocktake));
    }

    // FUNCTION 2: StocktakeService.handleConfirmStocktake
    @Test
    void confirmStocktake_actualGreaterThanSystemCreatesPositiveAdjustment() {
        Stocktake stocktake = stocktake(12, 10);
        Inventory inventory = inventory(stocktake.getDetails().get(0).getProduct(), 10);
        stubStocktake(stocktake);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        stocktakeService.handleConfirmStocktake(2L);

        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(12, inventory.getQuantityOnHand());
        assertEquals(2, captor.getValue().getQuantityChange());
        assertEquals(InventoryTransactionTypeEnum.ADJUSTMENT, captor.getValue().getTransactionType());
        assertEquals(StocktakeStatusEnum.COMPLETED, stocktake.getStatus());
        verify(lowStockAlertService).synchronizeForProduct(1L);
    }

    @Test
    void confirmStocktake_actualLessThanSystemCreatesNegativeAdjustment() {
        Stocktake stocktake = stocktake(8, 10);
        Inventory inventory = inventory(stocktake.getDetails().get(0).getProduct(), 10);
        stubStocktake(stocktake);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        stocktakeService.handleConfirmStocktake(2L);

        assertEquals(8, inventory.getQuantityOnHand());
        verify(transactionRepository).save(argThat(transaction -> transaction.getQuantityChange() == -2 && transaction.getQuantityAfter() == 8));
    }

    @Test
    void confirmStocktake_equalCountsCompletesWithoutAdjustment() {
        Stocktake stocktake = stocktake(10, 10);
        Inventory inventory = inventory(stocktake.getDetails().get(0).getProduct(), 10);
        stubStocktake(stocktake);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

        stocktakeService.handleConfirmStocktake(2L);

        verify(transactionRepository, never()).save(any());
        assertEquals(10, inventory.getQuantityOnHand());
        assertEquals(StocktakeStatusEnum.COMPLETED, stocktake.getStatus());
        verify(lowStockAlertService).synchronizeForProduct(1L);
    }

    @Test
    void confirmStocktake_missingStocktakeThrows() {
        when(stocktakeRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> stocktakeService.handleConfirmStocktake(99L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmStocktake_wrongStatusThrows() {
        Stocktake stocktake = stocktake(10, 10);
        stocktake.setStatus(StocktakeStatusEnum.COMPLETED);
        stubStocktake(stocktake);
        assertThrows(IllegalStateException.class, () -> stocktakeService.handleConfirmStocktake(2L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmStocktake_emptyDetailsThrows() {
        Stocktake stocktake = new Stocktake();
        stocktake.setId(2L);
        stocktake.setStatus(StocktakeStatusEnum.DRAFT);
        stocktake.setDetails(new ArrayList<>());
        stubStocktake(stocktake);
        assertThrows(IllegalStateException.class, () -> stocktakeService.handleConfirmStocktake(2L));
        verifyNoInteractions(inventoryRepository, transactionRepository);
    }

    @Test
    void confirmStocktake_missingInventoryThrows() {
        Stocktake stocktake = stocktake(10, 10);
        stubStocktake(stocktake);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> stocktakeService.handleConfirmStocktake(2L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmStocktake_changedSnapshotThrows() {
        Stocktake stocktake = stocktake(10, 9);
        Inventory inventory = inventory(stocktake.getDetails().get(0).getProduct(), 10);
        stubStocktake(stocktake);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        assertThrows(IllegalStateException.class, () -> stocktakeService.handleConfirmStocktake(2L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmStocktake_negativeInventoryIsRejected() {
        Stocktake stocktake = stocktake(10, 10);
        Inventory inventory = inventory(stocktake.getDetails().get(0).getProduct(), -1);
        stubStocktake(stocktake);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        assertThrows(IllegalStateException.class, () -> stocktakeService.handleConfirmStocktake(2L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmStocktake_inconsistentDifferenceThrows() {
        Stocktake stocktake = stocktake(12, 10);
        stocktake.getDetails().get(0).setQuantityDifference(99);
        Inventory inventory = inventory(stocktake.getDetails().get(0).getProduct(), 10);
        stubStocktake(stocktake);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        assertThrows(IllegalStateException.class, () -> stocktakeService.handleConfirmStocktake(2L));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmStocktake_multipleLinesAdjustsEachProduct() {
        Stocktake stocktake = stocktake(12, 10);
        Product secondProduct = product(2);
        StocktakeDetail second = new StocktakeDetail();
        second.setStocktake(stocktake);
        second.setProduct(secondProduct);
        second.setQuantitySystem(4);
        second.setQuantityActual(6);
        second.setQuantityDifference(2);
        stocktake.getDetails().add(second);
        Inventory firstInventory = inventory(stocktake.getDetails().get(0).getProduct(), 10);
        Inventory secondInventory = inventory(secondProduct, 4);
        stubStocktake(stocktake);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(firstInventory));
        when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(secondInventory));

        stocktakeService.handleConfirmStocktake(2L);

        assertEquals(12, firstInventory.getQuantityOnHand());
        assertEquals(6, secondInventory.getQuantityOnHand());
        verify(transactionRepository, times(2)).save(any(InventoryTransaction.class));
        verify(lowStockAlertService).synchronizeForProduct(1L);
        verify(lowStockAlertService).synchronizeForProduct(2L);
    }
}

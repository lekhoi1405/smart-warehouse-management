package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.LowStockAlert;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.repository.LowStockAlertRepository;
import com.lekhoi.smart_warehouse.util.constant.LowStockAlertStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

@ExtendWith(MockitoExtension.class)
class LowStockAlertServiceTest {

    @Mock private LowStockAlertRepository alertRepository;
    @Mock private InventoryRepository inventoryRepository;
    @InjectMocks private LowStockAlertService lowStockAlertService;

    private Product product(int threshold) {
        Product product = Product.builder().productCode("P-1").productName("Product")
            .unit("unit").status(ProductStatusEnum.ACTIVE).minStockThreshold(threshold).build();
        product.setId(1L);
        return product;
    }

    private Inventory inventory(Product product, int quantity) {
        return Inventory.builder().product(product).quantityOnHand(quantity).build();
    }

    // FUNCTION 3: LowStockAlertService.synchronizeForProduct
    @Test
    void synchronizeForProduct_belowThresholdCreatesNewAlert() {
        Product product = product(5);
        Inventory inventory = inventory(product, 4);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));
        when(alertRepository.findOpenByProductIdForUpdate(1L, LowStockAlertStatusEnum.RESOLVED)).thenReturn(List.of());

        lowStockAlertService.synchronizeForProduct(1L);

        verify(alertRepository).save(argThat(alert -> alert.getProduct() == product && alert.getQuantityAtAlert() == 4
            && alert.getStatus() == LowStockAlertStatusEnum.NEW));
    }

    @Test
    void synchronizeForProduct_atThresholdCreatesAlert() {
        Product product = product(5);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(product, 5)));
        when(alertRepository.findOpenByProductIdForUpdate(1L, LowStockAlertStatusEnum.RESOLVED)).thenReturn(List.of());

        lowStockAlertService.synchronizeForProduct(1L);

        verify(alertRepository).save(any(LowStockAlert.class));
    }

    @Test
    void synchronizeForProduct_aboveThresholdDoesNotCreateAlert() {
        Product product = product(5);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(product, 6)));
        when(alertRepository.findOpenByProductIdForUpdate(1L, LowStockAlertStatusEnum.RESOLVED)).thenReturn(List.of());

        lowStockAlertService.synchronizeForProduct(1L);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void synchronizeForProduct_existingAlertPreventsDuplicate() {
        Product product = product(5);
        LowStockAlert existing = new LowStockAlert();
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(product, 4)));
        when(alertRepository.findOpenByProductIdForUpdate(1L, LowStockAlertStatusEnum.RESOLVED)).thenReturn(List.of(existing));

        lowStockAlertService.synchronizeForProduct(1L);

        verify(alertRepository, never()).save(any());
    }

    @Test
    void synchronizeForProduct_aboveThresholdResolvesAllOpenAlerts() {
        Product product = product(5);
        LowStockAlert first = new LowStockAlert();
        LowStockAlert second = new LowStockAlert();
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(product, 8)));
        when(alertRepository.findOpenByProductIdForUpdate(1L, LowStockAlertStatusEnum.RESOLVED)).thenReturn(List.of(first, second));

        lowStockAlertService.synchronizeForProduct(1L);

        assertEquals(LowStockAlertStatusEnum.RESOLVED, first.getStatus());
        assertNotNull(first.getClosedAt());
        assertEquals(LowStockAlertStatusEnum.RESOLVED, second.getStatus());
        assertNotNull(second.getClosedAt());
    }

    @Test
    void synchronizeForProduct_missingInventoryThrows() {
        when(inventoryRepository.findByProductIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> lowStockAlertService.synchronizeForProduct(99L));
    }

    @Test
    void synchronizeForProduct_negativeQuantityOrThresholdThrows() {
        Product product = product(-1);
        when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory(product, -1)));

        assertThrows(IllegalStateException.class, () -> lowStockAlertService.synchronizeForProduct(1L));

        verifyNoInteractions(alertRepository);
    }
}

package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lekhoi.smart_warehouse.domain.entity.InventoryTransaction;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.repository.InventoryTransactionRepository;
import com.lekhoi.smart_warehouse.repository.ProductRepository;
import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.StatisticsGroupEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private StatisticsService statisticsService;

    private InventoryTransaction movement(InventoryTransactionTypeEnum type, int change, Instant createdAt) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setId(1L);
        transaction.setTransactionType(type);
        transaction.setQuantityChange(change);
        transaction.setCreatedAt(createdAt);
        return transaction;
    }

    // FUNCTION 4: StatisticsService.handleGetMovements
    @Test
    void getMovements_groupsByDayAndTotalsImportsExports() {
        LocalDate day = LocalDate.of(2025, 3, 1);
        Instant createdAt = day.atTime(10, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        when(transactionRepository.findConfirmedMovementsForReport(any(), any(), eq(StockDocumentStatusEnum.COMPLETED),
            eq(InventoryTransactionTypeEnum.IMPORT), eq(InventoryTransactionTypeEnum.EXPORT))).thenReturn(List.of(
            movement(InventoryTransactionTypeEnum.IMPORT, 5, createdAt),
            movement(InventoryTransactionTypeEnum.EXPORT, -2, createdAt)));

        var result = statisticsService.handleGetMovements(StatisticsGroupEnum.DAY, day, day.plusDays(1));

        assertEquals(5, result.totalImported());
        assertEquals(2, result.totalExported());
        assertEquals("2025-03-01", result.rows().get(0).period());
        assertEquals(5, result.rows().get(0).imported());
        assertEquals(2, result.rows().get(0).exported());
    }

    @Test
    void getMovements_groupsDatesByMonth() {
        Instant first = Instant.parse("2025-03-01T03:00:00Z");
        Instant second = Instant.parse("2025-03-20T03:00:00Z");
        when(transactionRepository.findConfirmedMovementsForReport(any(), any(), eq(StockDocumentStatusEnum.COMPLETED),
            eq(InventoryTransactionTypeEnum.IMPORT), eq(InventoryTransactionTypeEnum.EXPORT))).thenReturn(List.of(
            movement(InventoryTransactionTypeEnum.IMPORT, 4, first),
            movement(InventoryTransactionTypeEnum.IMPORT, 6, second)));

        var result = statisticsService.handleGetMovements(StatisticsGroupEnum.MONTH,
            LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));

        assertEquals(1, result.rows().size());
        assertEquals("2025-03", result.rows().get(0).period());
        assertEquals(10, result.totalImported());
    }

    @Test
    void getMovements_groupsMonthsByYear() {
        when(transactionRepository.findConfirmedMovementsForReport(any(), any(), eq(StockDocumentStatusEnum.COMPLETED),
            eq(InventoryTransactionTypeEnum.IMPORT), eq(InventoryTransactionTypeEnum.EXPORT))).thenReturn(List.of(
            movement(InventoryTransactionTypeEnum.EXPORT, -3, Instant.parse("2025-01-15T03:00:00Z")),
            movement(InventoryTransactionTypeEnum.EXPORT, -7, Instant.parse("2025-11-15T03:00:00Z"))));

        var result = statisticsService.handleGetMovements(StatisticsGroupEnum.YEAR,
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertEquals(1, result.rows().size());
        assertEquals("2025", result.rows().get(0).period());
        assertEquals(10, result.totalExported());
    }

    @Test
    void getMovements_sameDayQueriesWholeHalfOpenDayInterval() {
        LocalDate day = LocalDate.of(2025, 4, 1);
        Instant lastMoment = Instant.parse("2025-04-01T16:59:59.999Z");
        when(transactionRepository.findConfirmedMovementsForReport(any(), any(), eq(StockDocumentStatusEnum.COMPLETED),
            eq(InventoryTransactionTypeEnum.IMPORT), eq(InventoryTransactionTypeEnum.EXPORT))).thenReturn(List.of(
            movement(InventoryTransactionTypeEnum.IMPORT, 1, lastMoment)));

        var result = statisticsService.handleGetMovements(StatisticsGroupEnum.DAY, day, day);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(transactionRepository).findConfirmedMovementsForReport(from.capture(), to.capture(),
            eq(StockDocumentStatusEnum.COMPLETED), eq(InventoryTransactionTypeEnum.IMPORT), eq(InventoryTransactionTypeEnum.EXPORT));
        assertEquals(day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), from.getValue());
        assertEquals(day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), to.getValue());
        assertEquals(1, result.totalImported());
        assertEquals("2025-04-01", result.rows().get(0).period());
    }

    @Test
    void getMovements_noTransactionsReturnsEmptyTotals() {
        when(transactionRepository.findConfirmedMovementsForReport(any(), any(), eq(StockDocumentStatusEnum.COMPLETED),
            eq(InventoryTransactionTypeEnum.IMPORT), eq(InventoryTransactionTypeEnum.EXPORT))).thenReturn(List.of());

        var result = statisticsService.handleGetMovements(StatisticsGroupEnum.DAY,
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1));

        assertTrue(result.rows().isEmpty());
        assertEquals(0, result.totalImported());
        assertEquals(0, result.totalExported());
    }

    @Test
    void getMovements_invalidDateRangeRejectedBeforeRepositoryCall() {
        assertThrows(IllegalArgumentException.class, () -> statisticsService.handleGetMovements(
            StatisticsGroupEnum.DAY, LocalDate.of(2025, 2, 2), LocalDate.of(2025, 2, 1)));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getMovements_importWithNonPositiveQuantityChangeRejected() {
        when(transactionRepository.findConfirmedMovementsForReport(any(), any(), eq(StockDocumentStatusEnum.COMPLETED),
            eq(InventoryTransactionTypeEnum.IMPORT), eq(InventoryTransactionTypeEnum.EXPORT))).thenReturn(List.of(
            movement(InventoryTransactionTypeEnum.IMPORT, 0, Instant.parse("2025-01-01T00:00:00Z"))));

        assertThrows(IllegalStateException.class, () -> statisticsService.handleGetMovements(
            StatisticsGroupEnum.DAY, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)));
    }

    @Test
    void getMovements_exportWithNonNegativeQuantityChangeRejected() {
        when(transactionRepository.findConfirmedMovementsForReport(any(), any(), eq(StockDocumentStatusEnum.COMPLETED),
            eq(InventoryTransactionTypeEnum.IMPORT), eq(InventoryTransactionTypeEnum.EXPORT))).thenReturn(List.of(
            movement(InventoryTransactionTypeEnum.EXPORT, 1, Instant.parse("2025-01-01T00:00:00Z"))));

        assertThrows(IllegalStateException.class, () -> statisticsService.handleGetMovements(
            StatisticsGroupEnum.DAY, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)));
    }
}

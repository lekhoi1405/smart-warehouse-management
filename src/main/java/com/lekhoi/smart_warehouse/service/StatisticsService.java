package com.lekhoi.smart_warehouse.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.lekhoi.smart_warehouse.domain.dto.StatisticsDTO;
import com.lekhoi.smart_warehouse.domain.entity.Inventory;
import com.lekhoi.smart_warehouse.domain.entity.InventoryTransaction;
import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.domain.entity.StockDocumentDetail;
import com.lekhoi.smart_warehouse.domain.entity.StocktakeDetail;
import com.lekhoi.smart_warehouse.repository.InventoryRepository;
import com.lekhoi.smart_warehouse.repository.InventoryTransactionRepository;
import com.lekhoi.smart_warehouse.repository.ProductRepository;
import com.lekhoi.smart_warehouse.util.constant.InventoryTransactionTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.StatisticsGroupEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentTypeEnum;
import com.lekhoi.smart_warehouse.util.constant.StockPeriodEnum;
import com.lekhoi.smart_warehouse.util.constant.StocktakeStatusEnum;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final InventoryTransactionRepository transactionRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StatisticsDTO.MovementResponse handleGetMovements(StatisticsGroupEnum groupBy, LocalDate from, LocalDate to) {
        if (groupBy == null || from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid movement statistics period");
        }

        Instant start = from.atStartOfDay(REPORT_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        List<InventoryTransaction> transactions = transactionRepository.findConfirmedMovementsForReport(
            start, end, StockDocumentStatusEnum.COMPLETED,
            InventoryTransactionTypeEnum.IMPORT, InventoryTransactionTypeEnum.EXPORT
        );

        Map<String, long[]> grouped = new TreeMap<>();
        long totalImported = 0;
        long totalExported = 0;

        for (InventoryTransaction transaction : transactions) {
            InventoryTransactionTypeEnum type = transaction.getTransactionType();
            int change = transaction.getQuantityChange();

            if ((type == InventoryTransactionTypeEnum.IMPORT && change <= 0)
                    || (type == InventoryTransactionTypeEnum.EXPORT && change >= 0)) {
                throw new IllegalStateException("Invalid inventory transaction: " + transaction.getId());
            }

            long quantity = Math.abs((long) change);
            LocalDate date = transaction.getCreatedAt().atZone(REPORT_ZONE).toLocalDate();

            String period = switch (groupBy) {
                case DAY -> date.toString();
                case MONTH -> YearMonth.from(date).toString();
                case YEAR -> String.valueOf(date.getYear());
            };

            long[] values = grouped.computeIfAbsent(period, key -> new long[2]);

            if (type == InventoryTransactionTypeEnum.IMPORT) {
                values[0] = Math.addExact(values[0], quantity);
                totalImported = Math.addExact(totalImported, quantity);
            } else {
                values[1] = Math.addExact(values[1], quantity);
                totalExported = Math.addExact(totalExported, quantity);
            }
        }

        List<StatisticsDTO.MovementRow> rows = grouped.entrySet().stream()
            .map(entry -> new StatisticsDTO.MovementRow(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
            .toList();

        return new StatisticsDTO.MovementResponse(groupBy, from, to, totalImported, totalExported, rows);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StatisticsDTO.ClosingStockResponse handleGetClosingStock(StockPeriodEnum periodType, int year, Integer month) {
        LocalDate periodEnd = resolvePeriodEnd(periodType, year, month);
        Instant cutoff = periodEnd.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        List<Inventory> inventories = inventoryRepository.findAllWithProductForReport();

        if (inventories.size() != productRepository.count()) {
            throw new IllegalStateException("Some products do not have inventory records");
        }

        Map<Long, Product> products = new HashMap<>();
        Map<Long, Integer> reconstructed = new HashMap<>();
        Map<Long, Integer> closingBalances = new HashMap<>();

        for (Inventory inventory : inventories) {
            Product product = inventory.getProduct();

            if (product.getCreatedAt() == null) {
                throw new IllegalStateException("Missing product creation date: " + product.getId());
            }

            if (inventory.getQuantityOnHand() == null || inventory.getQuantityOnHand() < 0) {
                throw new IllegalStateException("Invalid current inventory: " + product.getId());
            }

            products.put(product.getId(), product);
            reconstructed.put(product.getId(), 0);
            closingBalances.put(product.getId(), 0);
        }

        List<InventoryTransaction> transactions = transactionRepository.findAllOrderedForReport();

        for (InventoryTransaction transaction : transactions) {
            Long productId = transaction.getProduct().getId();
            Product product = products.get(productId);

            if (product == null) {
                throw new IllegalStateException("Missing inventory for transaction product: " + productId);
            }

            if (transaction.getCreatedAt() == null || transaction.getCreatedAt().isBefore(product.getCreatedAt())) {
                throw new IllegalStateException("Invalid transaction date: " + transaction.getId());
            }

            Integer before = transaction.getQuantityBefore();
            Integer after = transaction.getQuantityAfter();
            Integer change = transaction.getQuantityChange();

            if (before == null || after == null || change == null) {
                throw new IllegalStateException("Incomplete transaction: " + transaction.getId());
            }

            long expectedAfter = (long) before + change;

            if (!before.equals(reconstructed.get(productId)) || expectedAfter != after || after < 0) {
                throw new IllegalStateException("Incomplete or inconsistent inventory history for product: " + productId);
            }

            validateTransactionSource(transaction);
            reconstructed.put(productId, after);

            if (transaction.getCreatedAt().isBefore(cutoff)) {
                closingBalances.put(productId, after);
            }
        }

        for (Inventory inventory : inventories) {
            Long productId = inventory.getProduct().getId();

            if (!reconstructed.get(productId).equals(inventory.getQuantityOnHand())) {
                throw new IllegalStateException("Inventory does not match transaction history for product: " + productId);
            }
        }

        List<StatisticsDTO.ClosingStockRow> rows = new ArrayList<>();

        for (Product product : products.values()) {
            if (!product.getCreatedAt().isBefore(cutoff)) continue;

            rows.add(new StatisticsDTO.ClosingStockRow(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                product.getUnit(),
                closingBalances.get(product.getId())
            ));
        }

        rows.sort(Comparator.comparing(StatisticsDTO.ClosingStockRow::productCode).thenComparing(StatisticsDTO.ClosingStockRow::productId));

        return new StatisticsDTO.ClosingStockResponse(periodType, year, month, periodEnd, rows);
    }

    private LocalDate resolvePeriodEnd(StockPeriodEnum periodType, int year, Integer month) {
        if (periodType == null || year < 1 || year > 9998) {
            throw new IllegalArgumentException("Invalid closing stock period");
        }

        LocalDate periodEnd;

        if (periodType == StockPeriodEnum.MONTH) {
            if (month == null || month < 1 || month > 12) {
                throw new IllegalArgumentException("Month must be between 1 and 12");
            }

            periodEnd = YearMonth.of(year, month).atEndOfMonth();
        } else {
            if (month != null) {
                throw new IllegalArgumentException("Month must be omitted for yearly reports");
            }

            periodEnd = LocalDate.of(year, 12, 31);
        }

        if (!periodEnd.isBefore(LocalDate.now(REPORT_ZONE))) {
            throw new IllegalArgumentException("The selected period has not ended");
        }

        return periodEnd;
    }

    private void validateTransactionSource(InventoryTransaction transaction) {
        InventoryTransactionTypeEnum type = transaction.getTransactionType();
        StockDocumentDetail documentDetail = transaction.getDocumentDetail();
        StocktakeDetail stocktakeDetail = transaction.getStocktakeDetail();

        if (type == InventoryTransactionTypeEnum.IMPORT || type == InventoryTransactionTypeEnum.EXPORT) {
            if (documentDetail == null || stocktakeDetail != null || documentDetail.getDocument() == null
                    || documentDetail.getDocument().getStatus() != StockDocumentStatusEnum.COMPLETED) {
                throw new IllegalStateException("Invalid document source for transaction: " + transaction.getId());
            }

            StockDocumentTypeEnum expectedType = type == InventoryTransactionTypeEnum.IMPORT
                ? StockDocumentTypeEnum.IMPORT : StockDocumentTypeEnum.EXPORT;

            if (documentDetail.getDocument().getDocumentType() != expectedType
                    || (type == InventoryTransactionTypeEnum.IMPORT && transaction.getQuantityChange() <= 0)
                    || (type == InventoryTransactionTypeEnum.EXPORT && transaction.getQuantityChange() >= 0)) {
                throw new IllegalStateException("Incorrect transaction type or quantity: " + transaction.getId());
            }

        } else if (type == InventoryTransactionTypeEnum.ADJUSTMENT) {
            if (documentDetail != null || stocktakeDetail == null || stocktakeDetail.getStocktake() == null
                    || stocktakeDetail.getStocktake().getStatus() != StocktakeStatusEnum.COMPLETED) {
                throw new IllegalStateException("Invalid stocktake source for transaction: " + transaction.getId());
            }
        } else {
            throw new IllegalStateException("Unsupported inventory transaction: " + transaction.getId());
        }
    }
}

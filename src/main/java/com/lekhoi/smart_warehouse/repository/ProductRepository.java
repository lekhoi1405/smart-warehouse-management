
package com.lekhoi.smart_warehouse.repository;

import com.lekhoi.smart_warehouse.domain.entity.Product;
import com.lekhoi.smart_warehouse.util.constant.ProductStatusEnum;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByProductCode(String productCode);

    boolean existsByProductCodeAndIdNot(
        String productCode,
        Long id
    );

    @Query("SELECT p FROM Product p "
                + "WHERE (:status IS NULL OR p.status = :status) "
                + "AND ("
                + ":category IS NULL "
                + "OR LOWER(p.category) = LOWER(:category)"
                + ") "
                + "AND ("
                + "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) "
                + "OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))"
                + ")")
    Page<Product> searchProducts(
        @Param("keyword") String keyword,
        @Param("category") String category,
        @Param("status") ProductStatusEnum status,
        Pageable pageable
    );
}

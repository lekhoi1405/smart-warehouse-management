package com.lekhoi.smart_warehouse.repository;

import com.lekhoi.smart_warehouse.domain.entity.Supplier;
import com.lekhoi.smart_warehouse.util.constant.SupplierStatusEnum;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsBySupplierCode(String supplierCode);

    boolean existsBySupplierCodeAndIdNot(String supplierCode, Long id);

    @Query(value = "SELECT s FROM Supplier s "
                + "WHERE (:status IS NULL OR s.status = :status) "
                + "AND ("
                + "LOWER(s.supplierCode) LIKE LOWER(CONCAT('%', :keyword, '%')) "
                + "OR LOWER(s.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%'))"
                + ")")
    Page<Supplier> searchSuppliers(@Param("keyword") String keyword, @Param("status") SupplierStatusEnum status, Pageable pageable);
}
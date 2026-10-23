package com.lekhoi.smart_warehouse.service;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.SupplierDTO;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination;
import com.lekhoi.smart_warehouse.domain.dto.response.ResultPagination.Meta;
import com.lekhoi.smart_warehouse.domain.entity.Supplier;
import com.lekhoi.smart_warehouse.repository.SupplierRepository;
import com.lekhoi.smart_warehouse.service.mapper.SupplierMapper;
import com.lekhoi.smart_warehouse.util.constant.SupplierStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;

    @Transactional
    public SupplierDTO.Response handleCreateSupplier(SupplierDTO.CreateRequest request) {
        String supplierCode = request.supplierCode().trim();

        if (supplierRepository.existsBySupplierCode(supplierCode)) {
            throw new AlreadyExistsException("Supplier code already exists");
        }

        Supplier supplier = supplierMapper.toEntity(request);
        supplier.setSupplierCode(supplierCode);
        supplier.setSupplierName(request.supplierName().trim());
        supplier.setStatus(SupplierStatusEnum.ACTIVE);

        return supplierMapper.toResponse(supplierRepository.save(supplier));
    }

    @Transactional(readOnly = true)
    public SupplierDTO.Response handleGetSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id).orElseThrow(() -> new IdInvalidException("Supplier not found: " + id));
        return supplierMapper.toResponse(supplier);
    }

    @Transactional(readOnly = true)
    public ResultPagination<List<SupplierDTO.Response>> handleGetAllSuppliers(int current, int pageSize, String keyword, SupplierStatusEnum status) {
        if (current < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("current must be >= 1 and pageSize must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(current - 1, pageSize, Sort.by("id").ascending());
        String searchKeyword = keyword == null ? "" : keyword.trim();

        Page<Supplier> supplierPage = supplierRepository.searchSuppliers(searchKeyword, status, pageable);
        List<SupplierDTO.Response> responses = supplierPage.getContent().stream().map(supplierMapper::toResponse).toList();

        Meta meta = Meta.builder().current(supplierPage.getNumber() + 1).pageSize(supplierPage.getSize()).pages(supplierPage.getTotalPages()).total(supplierPage.getTotalElements()).build();

        return ResultPagination.<List<SupplierDTO.Response>>builder().result(responses).meta(meta).build();
    }


    @Transactional
    public SupplierDTO.Response handleUpdateSupplier(SupplierDTO.UpdateRequest request) {
        Supplier supplier = supplierRepository.findById(request.id()).orElseThrow(() -> new IdInvalidException("Supplier not found: " + request.id()));

        if (supplier.getStatus() == SupplierStatusEnum.TERMINATED) {
            throw new IllegalStateException("Supplier has terminated cooperation and cannot be updated");
        }

        String supplierCode = request.supplierCode().trim();

        if (supplierRepository.existsBySupplierCodeAndIdNot(supplierCode, supplier.getId())) {
            throw new AlreadyExistsException("Supplier code already exists");
        }

        supplierMapper.update(request, supplier);
        supplier.setSupplierCode(supplierCode);
        supplier.setSupplierName(request.supplierName().trim());

        return supplierMapper.toResponse(supplier);
    }

    @Transactional
    public void handleDeleteSupplier(Long id) {
        Supplier supplier = supplierRepository.findById(id).orElseThrow(() -> new IdInvalidException("Supplier not found: " + id));

        if (supplier.getStatus() != SupplierStatusEnum.TERMINATED) {
            supplier.setStatus(SupplierStatusEnum.INACTIVE);
        }
    }

}
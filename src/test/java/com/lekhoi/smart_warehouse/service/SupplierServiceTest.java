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

import com.lekhoi.smart_warehouse.domain.dto.SupplierDTO;
import com.lekhoi.smart_warehouse.domain.entity.Supplier;
import com.lekhoi.smart_warehouse.repository.SupplierRepository;
import com.lekhoi.smart_warehouse.service.mapper.SupplierMapper;
import com.lekhoi.smart_warehouse.util.constant.SupplierStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

@ExtendWith(MockitoExtension.class)
class Function10To14Test {

    @Mock private SupplierRepository supplierRepository;
    @Mock private SupplierMapper supplierMapper;
    @InjectMocks private SupplierService supplierService;

    private Supplier supplier;
    private SupplierDTO.Response response;

    @BeforeEach
    void setUp() {
        supplier = Supplier.builder().supplierCode("S-01").supplierName("Supplier")
            .contactName("Contact").phone("123").email("a@example.com").address("Address")
            .status(SupplierStatusEnum.ACTIVE).build();
        supplier.setId(1L);
        response = new SupplierDTO.Response(1L, "S-01", "Supplier", "Contact", "123", "a@example.com", "Address", SupplierStatusEnum.ACTIVE);
    }

    // FUNCTION 10: handleCreateSupplier
    @Test
    void createSupplier_trimsFieldsAndSetsActiveStatus() {
        SupplierDTO.CreateRequest request = new SupplierDTO.CreateRequest(" S-01 ", " Supplier ", "Contact", "123", "a@example.com", "Address");
        when(supplierMapper.toEntity(request)).thenReturn(supplier);
        when(supplierRepository.save(supplier)).thenReturn(supplier);
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        assertEquals(response, supplierService.handleCreateSupplier(request));
        assertEquals("S-01", supplier.getSupplierCode());
        assertEquals("Supplier", supplier.getSupplierName());
        assertEquals(SupplierStatusEnum.ACTIVE, supplier.getStatus());
    }

    @Test
    void createSupplier_duplicateCodeThrows() {
        SupplierDTO.CreateRequest request = new SupplierDTO.CreateRequest("S-01", "Supplier", null, null, null, null);
        when(supplierRepository.existsBySupplierCode("S-01")).thenReturn(true);
        assertThrows(AlreadyExistsException.class, () -> supplierService.handleCreateSupplier(request));
        verify(supplierRepository, never()).save(any());
    }

    // FUNCTION 11: handleGetSupplierById
    @Test
    void getSupplierById_returnsMappedSupplier() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierMapper.toResponse(supplier)).thenReturn(response);
        assertEquals(response, supplierService.handleGetSupplierById(1L));
    }

    @Test
    void getSupplierById_missingSupplierThrows() {
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> supplierService.handleGetSupplierById(99L));
    }

    // FUNCTION 12: handleGetAllSuppliers
    @Test
    void getAllSuppliers_rejectsInvalidPagination() {
        assertThrows(IllegalArgumentException.class, () -> supplierService.handleGetAllSuppliers(0, 10, null, null));
        assertThrows(IllegalArgumentException.class, () -> supplierService.handleGetAllSuppliers(1, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> supplierService.handleGetAllSuppliers(1, 101, null, null));
    }

    @Test
    void getAllSuppliers_trimsKeywordAndReturnsPagination() {
        when(supplierRepository.searchSuppliers(eq("supplier"), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(supplier)));
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        var result = supplierService.handleGetAllSuppliers(1, 10, " supplier ", null);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(response, result.getResult().get(0));
    }

    // FUNCTION 13: handleUpdateSupplier
    @Test
    void updateSupplier_updatesTrimmedFields() {
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(1L, " S-02 ", " Updated ", "Contact", "123", "a@example.com", "Address", SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndIdNot("S-02", 1L)).thenReturn(false);
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        assertEquals(response, supplierService.handleUpdateSupplier(request));
        verify(supplierMapper).update(request, supplier);
        assertEquals("S-02", supplier.getSupplierCode());
        assertEquals("Updated", supplier.getSupplierName());
    }

    @Test
    void updateSupplier_terminatedSupplierCannotBeChanged() {
        supplier.setStatus(SupplierStatusEnum.TERMINATED);
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(1L, "S-02", "Updated", null, null, null, null, SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        assertThrows(IllegalStateException.class, () -> supplierService.handleUpdateSupplier(request));
        verify(supplierMapper, never()).update(any(), any());
    }

    @Test
    void updateSupplier_duplicateCodeThrows() {
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(1L, "S-02", "Updated", null, null, null, null, SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndIdNot("S-02", 1L)).thenReturn(true);
        assertThrows(AlreadyExistsException.class, () -> supplierService.handleUpdateSupplier(request));
    }

    @Test
    void updateSupplier_missingSupplierThrows() {
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(99L, "S-02", "Updated", null, null, null, null, SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> supplierService.handleUpdateSupplier(request));
    }

    // FUNCTION 14: handleDeleteSupplier
    @Test
    void deleteSupplier_marksNonTerminatedSupplierInactive() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        supplierService.handleDeleteSupplier(1L);
        assertEquals(SupplierStatusEnum.INACTIVE, supplier.getStatus());
    }

    @Test
    void deleteSupplier_keepsTerminatedStatus() {
        supplier.setStatus(SupplierStatusEnum.TERMINATED);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        supplierService.handleDeleteSupplier(1L);
        assertEquals(SupplierStatusEnum.TERMINATED, supplier.getStatus());
    }

    @Test
    void deleteSupplier_missingSupplierThrows() {
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IdInvalidException.class, () -> supplierService.handleDeleteSupplier(99L));
    }
}

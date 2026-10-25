package com.lekhoi.smart_warehouse.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.lekhoi.smart_warehouse.domain.dto.SupplierDTO;
import com.lekhoi.smart_warehouse.domain.entity.Supplier;
import com.lekhoi.smart_warehouse.repository.SupplierRepository;
import com.lekhoi.smart_warehouse.service.mapper.SupplierMapper;
import com.lekhoi.smart_warehouse.util.constant.SupplierStatusEnum;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

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
    void createSupplier_validRequestSavesSupplierAsActive() {
        SupplierDTO.CreateRequest request = new SupplierDTO.CreateRequest("S-01", "Supplier", "Contact", "123", "a@example.com", "Address");
        when(supplierMapper.toEntity(request)).thenReturn(supplier);
        when(supplierRepository.save(supplier)).thenReturn(supplier);
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        assertEquals(response, supplierService.handleCreateSupplier(request));
        assertEquals("S-01", supplier.getSupplierCode());
        assertEquals("Supplier", supplier.getSupplierName());
        assertEquals(SupplierStatusEnum.ACTIVE, supplier.getStatus());
        verify(supplierRepository).existsBySupplierCode("S-01");
        verify(supplierMapper).toEntity(request);
        verify(supplierRepository).save(supplier);
        verify(supplierMapper).toResponse(supplier);
    }

    @Test
    void createSupplier_duplicateCodeThrows() {
        SupplierDTO.CreateRequest request = new SupplierDTO.CreateRequest("S-01", "Supplier", null, null, null, null);
        when(supplierRepository.existsBySupplierCode("S-01")).thenReturn(true);

        assertThrows(AlreadyExistsException.class, () -> supplierService.handleCreateSupplier(request));

        verify(supplierRepository, never()).save(any());
        verifyNoInteractions(supplierMapper);
    }

    @Test
    void createSupplier_trimsCodeAndNameBeforeSaving() {
        SupplierDTO.CreateRequest request = new SupplierDTO.CreateRequest(" SUP-02 ", " Supplier B ", null, null, null, null);
        when(supplierMapper.toEntity(request)).thenReturn(supplier);
        when(supplierRepository.save(supplier)).thenReturn(supplier);
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        supplierService.handleCreateSupplier(request);

        verify(supplierRepository).existsBySupplierCode("SUP-02");
        verify(supplierRepository).save(supplier);
        assertEquals("SUP-02", supplier.getSupplierCode());
        assertEquals("Supplier B", supplier.getSupplierName());
    }

    @Test
    void createSupplier_mapsOptionalContactFieldsFromRequest() {
        SupplierDTO.CreateRequest request = new SupplierDTO.CreateRequest(
            "SUP-03", "Supplier C", "Contact C", "0900000000", "contact@example.com", "Warehouse address");
        Supplier mappedSupplier = Supplier.builder()
            .supplierCode(request.supplierCode())
            .supplierName(request.supplierName())
            .contactName(request.contactName())
            .phone(request.phone())
            .email(request.email())
            .address(request.address())
            .status(SupplierStatusEnum.INACTIVE)
            .build();
        mappedSupplier.setId(3L);
        when(supplierMapper.toEntity(request)).thenReturn(mappedSupplier);
        when(supplierRepository.save(mappedSupplier)).thenReturn(mappedSupplier);
        when(supplierMapper.toResponse(mappedSupplier)).thenReturn(response);

        supplierService.handleCreateSupplier(request);

        verify(supplierMapper).toEntity(request);
        verify(supplierRepository).save(mappedSupplier);
        assertEquals("Contact C", mappedSupplier.getContactName());
        assertEquals("0900000000", mappedSupplier.getPhone());
        assertEquals("contact@example.com", mappedSupplier.getEmail());
        assertEquals("Warehouse address", mappedSupplier.getAddress());
        assertEquals(SupplierStatusEnum.ACTIVE, mappedSupplier.getStatus());
    }

    @Test
    void createSupplier_blankNameFailsDtoValidation() {
        SupplierDTO.CreateRequest request = new SupplierDTO.CreateRequest("SUP-04", "   ", null, null, null, null);

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<SupplierDTO.CreateRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("supplierName")));
        }
    }

    @Test
    void createSupplier_invalidEmailFailsDtoValidation() {
        SupplierDTO.CreateRequest request = new SupplierDTO.CreateRequest(
            "SUP-05", "Supplier D", null, null, "not-an-email", null);

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<SupplierDTO.CreateRequest>> violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("email")));
        }
    }

    // FUNCTION 11: handleGetSupplierById
    @Test
    void getSupplierById_returnsMappedSupplier() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        assertEquals(response, supplierService.handleGetSupplierById(1L));

        verify(supplierRepository).findById(1L);
        verify(supplierMapper).toResponse(supplier);
    }

    @Test
    void getSupplierById_missingSupplierThrows() {
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        IdInvalidException exception = assertThrows(
            IdInvalidException.class,
            () -> supplierService.handleGetSupplierById(99L)
        );

        assertEquals("Supplier not found: 99", exception.getMessage());
        verify(supplierRepository).findById(99L);
        verifyNoInteractions(supplierMapper);
    }

    // FUNCTION 12: handleGetAllSuppliers
    @Test
    void getAllSuppliers_trimsKeywordAndFiltersByActiveStatus() {
        when(supplierRepository.searchSuppliers(eq("ABC"), eq(SupplierStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(supplier), PageRequest.of(0, 20), 1));
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        var result = supplierService.handleGetAllSuppliers(1, 20, " ABC ", SupplierStatusEnum.ACTIVE);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(1, result.getMeta().getCurrent());
        assertEquals(20, result.getMeta().getPageSize());
        assertEquals(1, result.getMeta().getPages());
        assertEquals(response, result.getResult().get(0));
        verify(supplierRepository).searchSuppliers(eq("ABC"), eq(SupplierStatusEnum.ACTIVE), any());
        verify(supplierMapper).toResponse(supplier);
    }

    @Test
    void getAllSuppliers_nullKeywordAndStatusUseEmptyKeywordAndNoStatusFilter() {
        when(supplierRepository.searchSuppliers(eq(""), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(supplier), PageRequest.of(0, 20), 1));
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        var result = supplierService.handleGetAllSuppliers(1, 20, null, null);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(response, result.getResult().get(0));
        verify(supplierRepository).searchSuppliers(eq(""), isNull(), any());
    }

    @Test
    void getAllSuppliers_blankKeywordIsTrimmedToEmpty() {
        when(supplierRepository.searchSuppliers(eq(""), eq(SupplierStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(supplier), PageRequest.of(0, 20), 1));
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        var result = supplierService.handleGetAllSuppliers(1, 20, "   ", SupplierStatusEnum.ACTIVE);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(response, result.getResult().get(0));
        verify(supplierRepository).searchSuppliers(eq(""), eq(SupplierStatusEnum.ACTIVE), any());
    }

    @Test
    void getAllSuppliers_filtersByInactiveStatus() {
        supplier.setStatus(SupplierStatusEnum.INACTIVE);
        SupplierDTO.Response inactiveResponse = new SupplierDTO.Response(
            1L, "S-01", "Supplier", "Contact", "123", "a@example.com", "Address", SupplierStatusEnum.INACTIVE);
        when(supplierRepository.searchSuppliers(eq(""), eq(SupplierStatusEnum.INACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(supplier), PageRequest.of(0, 20), 1));
        when(supplierMapper.toResponse(supplier)).thenReturn(inactiveResponse);

        var result = supplierService.handleGetAllSuppliers(1, 20, null, SupplierStatusEnum.INACTIVE);

        assertEquals(1, result.getMeta().getTotal());
        assertEquals(SupplierStatusEnum.INACTIVE, result.getResult().get(0).status());
        verify(supplierRepository).searchSuppliers(eq(""), eq(SupplierStatusEnum.INACTIVE), any());
    }

    @Test
    void getAllSuppliers_noKeywordMatchReturnsEmptyPage() {
        when(supplierRepository.searchSuppliers(eq("SUPPLIER_NOT_FOUND"), eq(SupplierStatusEnum.ACTIVE), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = supplierService.handleGetAllSuppliers(
            1, 20, "SUPPLIER_NOT_FOUND", SupplierStatusEnum.ACTIVE);

        assertTrue(result.getResult().isEmpty());
        assertEquals(0, result.getMeta().getTotal());
        assertEquals(0, result.getMeta().getPages());
        verify(supplierRepository).searchSuppliers(eq("SUPPLIER_NOT_FOUND"), eq(SupplierStatusEnum.ACTIVE), any());
        verifyNoInteractions(supplierMapper);
    }

    // FUNCTION 13: handleUpdateSupplier
    @Test
    void updateSupplier_updatesSupplierWithUniqueCode() {
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(
            1L, "S-02", "Updated", "Contact", "123", "a@example.com", "Address", SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndIdNot("S-02", 1L)).thenReturn(false);
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        assertEquals(response, supplierService.handleUpdateSupplier(request));
        verify(supplierRepository).findById(1L);
        verify(supplierRepository).existsBySupplierCodeAndIdNot("S-02", 1L);
        verify(supplierMapper).update(request, supplier);
        assertEquals("S-02", supplier.getSupplierCode());
        assertEquals("Updated", supplier.getSupplierName());
        verify(supplierMapper).toResponse(supplier);
    }

    @Test
    void updateSupplier_sameCodeDoesNotThrowWhenCodeIsAvailable() {
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(
            1L, "S-01", "Updated", "Contact", "123", "a@example.com", "Address", SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndIdNot("S-01", 1L)).thenReturn(false);
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        supplierService.handleUpdateSupplier(request);

        verify(supplierRepository).existsBySupplierCodeAndIdNot("S-01", 1L);
        verify(supplierMapper).update(request, supplier);
        assertEquals("S-01", supplier.getSupplierCode());
        verify(supplierMapper).toResponse(supplier);
    }

    @Test
    void updateSupplier_trimsCodeAndName() {
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(
            1L, " SUP-X ", " Công ty X ", "Contact", "123", "a@example.com", "Address", SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndIdNot("SUP-X", 1L)).thenReturn(false);
        when(supplierMapper.toResponse(supplier)).thenReturn(response);

        supplierService.handleUpdateSupplier(request);

        verify(supplierRepository).existsBySupplierCodeAndIdNot("SUP-X", 1L);
        verify(supplierMapper).update(request, supplier);
        assertEquals("SUP-X", supplier.getSupplierCode());
        assertEquals("Công ty X", supplier.getSupplierName());
    }

    @Test
    void updateSupplier_terminatedSupplierCannotBeChanged() {
        supplier.setStatus(SupplierStatusEnum.TERMINATED);
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(1L, "S-02", "Updated", null, null, null, null, SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

        assertThrows(IllegalStateException.class, () -> supplierService.handleUpdateSupplier(request));

        verify(supplierMapper, never()).update(any(), any());
        verify(supplierRepository, never()).existsBySupplierCodeAndIdNot(anyString(), anyLong());
        verifyNoInteractions(supplierMapper);
    }

    @Test
    void updateSupplier_duplicateCodeThrows() {
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(1L, "S-02", "Updated", null, null, null, null, SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(supplierRepository.existsBySupplierCodeAndIdNot("S-02", 1L)).thenReturn(true);

        assertThrows(AlreadyExistsException.class, () -> supplierService.handleUpdateSupplier(request));

        verify(supplierRepository).existsBySupplierCodeAndIdNot("S-02", 1L);
        verify(supplierMapper, never()).update(any(), any());
        verifyNoInteractions(supplierMapper);
    }

    @Test
    void updateSupplier_missingSupplierThrows() {
        SupplierDTO.UpdateRequest request = new SupplierDTO.UpdateRequest(99L, "S-02", "Updated", null, null, null, null, SupplierStatusEnum.ACTIVE);
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IdInvalidException.class, () -> supplierService.handleUpdateSupplier(request));

        verify(supplierRepository).findById(99L);
        verify(supplierRepository, never()).existsBySupplierCodeAndIdNot(anyString(), anyLong());
        verifyNoInteractions(supplierMapper);
    }

    // FUNCTION 14: handleDeleteSupplier
    @Test
    void deleteSupplier_marksNonTerminatedSupplierInactive() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

        supplierService.handleDeleteSupplier(1L);

        assertEquals(SupplierStatusEnum.INACTIVE, supplier.getStatus());
        verify(supplierRepository).findById(1L);
        verify(supplierRepository, never()).delete(any(Supplier.class));
        verify(supplierRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteSupplier_keepsTerminatedStatus() {
        supplier.setStatus(SupplierStatusEnum.TERMINATED);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

        supplierService.handleDeleteSupplier(1L);

        assertEquals(SupplierStatusEnum.TERMINATED, supplier.getStatus());
        verify(supplierRepository).findById(1L);
        verify(supplierRepository, never()).delete(any(Supplier.class));
        verify(supplierRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteSupplier_missingSupplierThrows() {
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        IdInvalidException exception = assertThrows(
            IdInvalidException.class,
            () -> supplierService.handleDeleteSupplier(99L)
        );

        assertEquals("Supplier not found: 99", exception.getMessage());
        verify(supplierRepository).findById(99L);
        verify(supplierRepository, never()).delete(any(Supplier.class));
        verify(supplierRepository, never()).deleteById(anyLong());
    }
}

package com.lekhoi.smart_warehouse.service.mapper;

import com.lekhoi.smart_warehouse.domain.dto.SupplierDTO;
import com.lekhoi.smart_warehouse.domain.entity.Supplier;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SupplierMapper {

    @Mapping(target = "status", ignore = true)
    Supplier toEntity(SupplierDTO.CreateRequest request);

    SupplierDTO.Response toResponse(Supplier supplier);

    @Mapping(target = "id", ignore = true)
    void update(SupplierDTO.UpdateRequest request, @MappingTarget Supplier supplier);
}
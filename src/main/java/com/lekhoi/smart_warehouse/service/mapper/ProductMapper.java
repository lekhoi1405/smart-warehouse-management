package com.lekhoi.smart_warehouse.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.lekhoi.smart_warehouse.domain.dto.ProductDTO;
import com.lekhoi.smart_warehouse.domain.entity.Product;

@Mapper (componentModel = "spring",nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Product toEntity(ProductDTO.CreateRequest request);

    ProductDTO.Response toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void update(ProductDTO.UpdateRequest request, @MappingTarget Product product);  
}

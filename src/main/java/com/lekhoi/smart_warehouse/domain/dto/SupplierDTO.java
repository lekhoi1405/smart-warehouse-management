package com.lekhoi.smart_warehouse.domain.dto;

import com.lekhoi.smart_warehouse.util.constant.SupplierStatusEnum;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface SupplierDTO {

    record CreateRequest(@NotBlank String supplierCode, @NotBlank String supplierName, String contactName, String phone, @Email String email, String address) {}

    record UpdateRequest(@NotNull Long id, @NotBlank String supplierCode, @NotBlank String supplierName, String contactName, String phone, @Email String email, String address, @NotNull SupplierStatusEnum status) {}

    record Response(Long id, String supplierCode, String supplierName, String contactName, String phone, String email, String address, SupplierStatusEnum status) {}
}
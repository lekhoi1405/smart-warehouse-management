package com.lekhoi.smart_warehouse.domain.entity;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permission")
@Getter
@Setter
@NoArgsConstructor
public class Permission extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String permissionName;

    private String description;
}

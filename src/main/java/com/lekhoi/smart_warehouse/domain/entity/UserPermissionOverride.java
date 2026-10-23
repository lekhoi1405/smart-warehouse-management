package com.lekhoi.smart_warehouse.domain.entity;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_permission_override", uniqueConstraints = @UniqueConstraint(name = "uk_account_permission", columnNames = {"account_id", "permission_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserPermissionOverride extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(nullable = false)
    private boolean granted;
}

package com.lekhoi.smart_warehouse.domain.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;
import com.lekhoi.smart_warehouse.util.constant.AccountStatusEnum;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String email;
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatusEnum status = AccountStatusEnum.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> userRoles = new HashSet<>();

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserPermissionOverride> permissionOverrides = new HashSet<>();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}

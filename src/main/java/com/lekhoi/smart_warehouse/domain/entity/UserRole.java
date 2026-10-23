package com.lekhoi.smart_warehouse.domain.entity;

import java.time.Instant;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_role", uniqueConstraints = @UniqueConstraint(name = "uk_user_role", columnNames = {"account_id", "role_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    private Instant assignedAt;

    @PrePersist
    public void onCreate() {
        if (assignedAt == null) assignedAt = Instant.now();
    }
}

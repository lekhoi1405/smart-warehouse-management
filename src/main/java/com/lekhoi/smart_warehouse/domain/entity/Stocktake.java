package com.lekhoi.smart_warehouse.domain.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;
import com.lekhoi.smart_warehouse.util.constant.StocktakeStatusEnum;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stocktake")
@Getter
@Setter
@NoArgsConstructor
public class Stocktake extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String stocktakeCode;

    @Column(nullable = false)
    private LocalDate stocktakeDate;

    @Column(name = "created_by")
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StocktakeStatusEnum status;

    @Column(columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StocktakeDetail> details = new ArrayList<>();
}
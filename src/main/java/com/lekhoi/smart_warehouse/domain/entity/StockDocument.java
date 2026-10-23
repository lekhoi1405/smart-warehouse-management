package com.lekhoi.smart_warehouse.domain.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lekhoi.smart_warehouse.domain.base.BaseEntity;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentStatusEnum;
import com.lekhoi.smart_warehouse.util.constant.StockDocumentTypeEnum;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_document")
@Getter
@Setter
@NoArgsConstructor
public class StockDocument extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String documentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockDocumentTypeEnum documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockDocumentStatusEnum status;

    @Column(columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockDocumentDetail> details = new ArrayList<>();
}

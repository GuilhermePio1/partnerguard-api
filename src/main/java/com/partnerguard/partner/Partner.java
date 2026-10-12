package com.partnerguard.partner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "partner")
class Partner {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    /** Normalized CNPJ. Never leaves this package unmasked (ADR-0010). */
    @Column(name = "tax_id", nullable = false, length = 14)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PartnerStatus status;

    protected Partner() {
    }

    private Partner(UUID id, String legalName, String taxId, PartnerStatus status) {
        this.id = id;
        this.legalName = legalName;
        this.taxId = taxId;
        this.status = status;
    }

    static Partner create(String legalName, String normalizedTaxId) {
        return new Partner(UUID.randomUUID(), legalName, normalizedTaxId, PartnerStatus.ACTIVE);
    }

    UUID getId() {
        return id;
    }

    String getLegalName() {
        return legalName;
    }

    String getTaxId() {
        return taxId;
    }

    PartnerStatus getStatus() {
        return status;
    }

    /** Excludes taxId so it can't reach a log line (PRT-14). */
    @Override
    public String toString() {
        return "Partner[id=" + id + ", status=" + status + "]";
    }
}

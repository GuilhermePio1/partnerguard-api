package com.partnerguard.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** A contract between the insurer and one partner. Read-only in this scope (ADR-0017). */
@Entity
@Table(name = "contract")
class Contract {

    @Id
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private String terms;

    protected Contract() {
    }

    UUID getId() {
        return id;
    }

    UUID getPartnerId() {
        return partnerId;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    String getTerms() {
        return terms;
    }
}

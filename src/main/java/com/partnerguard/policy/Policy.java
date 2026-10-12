package com.partnerguard.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** An insurance policy. Read-only in this scope (ADR-0017). */
@Entity
@Table(name = "policy")
class Policy {

    @Id
    private UUID id;

    @Column(name = "policy_number", nullable = false, length = 30)
    private String policyNumber;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "vehicle_plate", nullable = false, length = 7)
    private String vehiclePlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PolicyStatus status;

    protected Policy() {
    }

    UUID getId() {
        return id;
    }

    String getPolicyNumber() {
        return policyNumber;
    }

    String getHolderName() {
        return holderName;
    }

    String getVehiclePlate() {
        return vehiclePlate;
    }

    PolicyStatus getStatus() {
        return status;
    }
}

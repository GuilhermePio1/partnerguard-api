package com.partnerguard.claim;

import com.partnerguard.common.error.ProblemException;
import com.partnerguard.common.error.ProblemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "claim")
class Claim {

    @Id
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClaimStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected Claim() {
    }

    /** Moves to {@code target} or fails with 409 if the state machine doesn't allow it. */
    void changeStatus(ClaimStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ProblemException(ProblemType.INVALID_STATUS_TRANSITION);
        }
        this.status = target;
    }

    void changeDescription(String description) {
        this.description = description;
    }

    /** The caller validated the scale; HALF_EVEN only normalizes values like 10 to 10.00. */
    void changeAmount(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
    }

    UUID getId() {
        return id;
    }

    UUID getPolicyId() {
        return policyId;
    }

    UUID getPartnerId() {
        return partnerId;
    }

    String getDescription() {
        return description;
    }

    ClaimStatus getStatus() {
        return status;
    }

    BigDecimal getAmount() {
        return amount;
    }
}

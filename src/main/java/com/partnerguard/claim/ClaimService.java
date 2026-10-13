package com.partnerguard.claim;

import com.partnerguard.common.caller.Role;
import com.partnerguard.common.error.ProblemException;
import com.partnerguard.common.error.ProblemType;
import com.partnerguard.common.ownership.OwnershipDenials;
import com.partnerguard.common.ownership.OwnershipScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Ownership is enforced here, in the query itself, whoever the caller is (ADR-0004). */
@Service
@Transactional(readOnly = true)
class ClaimService {

    private final ClaimRepository claims;

    ClaimService(ClaimRepository claims) {
        this.claims = claims;
    }

    public Page<Claim> list(OwnershipScope scope, ClaimStatus status, Pageable pageable) {
        return switch (scope) {
            case OwnershipScope.Unrestricted ignored -> status == null
                    ? claims.findAll(pageable)
                    : claims.findAllByStatus(status, pageable);
            case OwnershipScope.PartnerOnly(var partnerId) -> status == null
                    ? claims.findAllByPartnerId(partnerId, pageable)
                    : claims.findAllByPartnerIdAndStatus(partnerId, status, pageable);
        };
    }

    public Claim get(OwnershipScope scope, UUID id) {
        Optional<Claim> claim = switch (scope) {
            case OwnershipScope.Unrestricted ignored -> claims.findById(id);
            case OwnershipScope.PartnerOnly(var partnerId) -> claims.findByIdAndPartnerId(id, partnerId);
        };
        return claim.orElseThrow(() -> OwnershipDenials.notFound(scope, "claim", id));
    }

    /**
     * Applies a PATCH. Order (specs/claims.md rule 4, ADR-0009): the controller has validated
     * the body (400); then field permissions for the editor's role (403), before any lookup;
     * then the ownership-scoped lookup (404); then state rules (409).
     *
     * <p>Milestone 6 adds the audit entry in this transaction.
     */
    @Transactional
    public Claim update(Role editor, OwnershipScope scope, UUID id, ClaimChanges changes) {
        ClaimPermissions.check(editor, changes);
        Claim claim = get(scope, id);
        if (editor == Role.PARTNER && claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new ProblemException(ProblemType.CLAIM_NOT_EDITABLE);
        }
        if (changes.status() != null) {
            claim.changeStatus(changes.status());
        }
        if (changes.description() != null) {
            claim.changeDescription(changes.description());
        }
        if (changes.amount() != null) {
            claim.changeAmount(changes.amount());
        }
        return claim;
    }
}

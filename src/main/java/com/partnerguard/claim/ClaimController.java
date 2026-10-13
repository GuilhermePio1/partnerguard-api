package com.partnerguard.claim;

import com.partnerguard.api.ClaimsApi;
import com.partnerguard.api.model.ClaimDto;
import com.partnerguard.api.model.ClaimPageDto;
import com.partnerguard.api.model.ClaimStatusDto;
import com.partnerguard.api.model.ClaimUpdateRequestDto;
import com.partnerguard.common.caller.CallerContext;
import com.partnerguard.common.error.ProblemException;
import com.partnerguard.common.web.SortOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class ClaimController implements ClaimsApi {

    private final ClaimService claims;
    private final CallerContext caller;

    ClaimController(ClaimService claims, CallerContext caller) {
        this.claims = claims;
        this.caller = caller;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'PARTNER')")
    public ResponseEntity<ClaimPageDto> listClaims(Integer page, Integer size, String sort, ClaimStatusDto status) {
        return ResponseEntity.ok(ClaimMapper.toPage(claims.list(caller.ownershipScope(), ClaimMapper.toDomain(status),
                SortOptions.pageRequest(page, size, sort, SortOptions.CLAIMS))));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'PARTNER')")
    public ResponseEntity<ClaimDto> getClaim(UUID id) {
        return ResponseEntity.ok(ClaimMapper.toDto(claims.get(caller.ownershipScope(), id)));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'PARTNER')")
    public ResponseEntity<ClaimDto> updateClaim(UUID id, ClaimUpdateRequestDto request) {
        validateBeyondSchema(request);
        return ResponseEntity.ok(ClaimMapper.toDto(
                claims.update(caller.role(), caller.ownershipScope(), id, ClaimMapper.toChanges(request))));
    }

    /** Contract rules the generated DTO can't enforce: minProperties and multipleOf (ADR-0007). */
    private static void validateBeyondSchema(ClaimUpdateRequestDto request) {
        if (request.getStatus() == null && request.getDescription() == null && request.getAmount() == null) {
            throw ProblemException.invalid("body", "must contain at least one field");
        }
        if (request.getAmount() != null && request.getAmount().stripTrailingZeros().scale() > 2) {
            throw ProblemException.invalid("amount", "must have at most 2 decimal places");
        }
    }
}

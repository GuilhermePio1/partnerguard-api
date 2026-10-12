package com.partnerguard.claim;

import com.partnerguard.api.model.ClaimDto;
import com.partnerguard.api.model.ClaimPageDto;
import com.partnerguard.api.model.ClaimStatusDto;
import com.partnerguard.api.model.ClaimUpdateRequestDto;
import org.springframework.data.domain.Page;

final class ClaimMapper {

    private ClaimMapper() {
    }

    static ClaimDto toDto(Claim claim) {
        return new ClaimDto(claim.getId(), claim.getPolicyId(), claim.getPartnerId(), claim.getDescription(),
                toDto(claim.getStatus()), claim.getAmount());
    }

    static ClaimPageDto toPage(Page<Claim> page) {
        return new ClaimPageDto(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.map(ClaimMapper::toDto).getContent());
    }

    static ClaimChanges toChanges(ClaimUpdateRequestDto request) {
        return new ClaimChanges(toDomain(request.getStatus()), request.getDescription(), request.getAmount());
    }

    static ClaimStatus toDomain(ClaimStatusDto status) {
        return status == null ? null : ClaimStatus.valueOf(status.name());
    }

    private static ClaimStatusDto toDto(ClaimStatus status) {
        return ClaimStatusDto.valueOf(status.name());
    }
}

package com.partnerguard.partner;

import com.partnerguard.api.PartnersApi;
import com.partnerguard.api.model.PartnerCreateRequestDto;
import com.partnerguard.api.model.PartnerDto;
import com.partnerguard.api.model.PartnerPageDto;
import com.partnerguard.common.caller.CallerContext;
import com.partnerguard.common.web.SortOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
class PartnerController implements PartnersApi {

    private final PartnerService partners;
    private final CallerContext caller;

    PartnerController(PartnerService partners, CallerContext caller) {
        this.partners = partners;
        this.caller = caller;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<PartnerPageDto> listPartners(Integer page, Integer size, String sort) {
        return ResponseEntity.ok(PartnerMapper.toPage(
                partners.list(SortOptions.pageRequest(page, size, sort, SortOptions.PARTNERS))));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PartnerDto> createPartner(PartnerCreateRequestDto request) {
        Partner created = partners.create(request.getLegalName(), request.getTaxId());
        // Relative Location: never built from the Host header.
        return ResponseEntity.created(URI.create("/api/v1/partners/" + created.getId()))
                .body(PartnerMapper.toDto(created));
    }

    @Override
    @PreAuthorize("hasRole('PARTNER')")
    public ResponseEntity<PartnerDto> getCurrentPartner() {
        // Fail closed: no partner identity, no profile (ADR-0003).
        UUID partnerId = caller.partnerId().orElseThrow(() -> new AccessDeniedException("No partner identity"));
        return ResponseEntity.ok(PartnerMapper.toDto(partners.get(partnerId)));
    }
}

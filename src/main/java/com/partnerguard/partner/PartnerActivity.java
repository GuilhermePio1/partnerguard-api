package com.partnerguard.partner;

import com.partnerguard.common.caller.ActivePartners;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Answers the security gate's "may this partner use the API?" (ADR-0021). */
@Component
@NullMarked
class PartnerActivity implements ActivePartners {

    private final PartnerRepository partners;

    PartnerActivity(PartnerRepository partners) {
        this.partners = partners;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID partnerId) {
        return partners.existsByIdAndStatus(partnerId, PartnerStatus.ACTIVE);
    }
}

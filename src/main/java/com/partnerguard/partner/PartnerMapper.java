package com.partnerguard.partner;

import com.partnerguard.api.model.PartnerDto;
import com.partnerguard.api.model.PartnerPageDto;
import com.partnerguard.api.model.PartnerStatusDto;
import com.partnerguard.common.masking.TaxIdMasker;
import org.springframework.data.domain.Page;

final class PartnerMapper {

    private PartnerMapper() {
    }

    static PartnerDto toDto(Partner partner) {
        return new PartnerDto(
                partner.getId(),
                partner.getLegalName(),
                TaxIdMasker.mask(partner.getTaxId()),
                PartnerStatusDto.valueOf(partner.getStatus().name()));
    }

    static PartnerPageDto toPage(Page<Partner> page) {
        return new PartnerPageDto(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.map(PartnerMapper::toDto).getContent());
    }
}

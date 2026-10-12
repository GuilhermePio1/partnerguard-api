package com.partnerguard.partner;

import com.partnerguard.common.error.ProblemException;
import com.partnerguard.common.error.ProblemType;
import com.partnerguard.common.error.ResourceNotFoundException;
import com.partnerguard.common.validation.Cnpj;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
class PartnerService {

    private final PartnerRepository partners;

    PartnerService(PartnerRepository partners) {
        this.partners = partners;
    }

    public Page<Partner> list(Pageable pageable) {
        return partners.findAll(pageable);
    }

    public Partner get(UUID id) {
        return partners.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional
    public Partner create(String legalName, String taxId) {
        String normalizedTaxId = Cnpj.normalize(taxId)
                .orElseThrow(() -> ProblemException.invalid("taxId", "must be a valid CNPJ"));
        if (partners.existsByTaxId(normalizedTaxId)) {
            throw new ProblemException(ProblemType.DUPLICATE_TAX_ID);
        }
        try {
            return partners.saveAndFlush(Partner.create(legalName, normalizedTaxId));
        } catch (DataIntegrityViolationException e) {
            // A concurrent insert won the race on uq_partner_tax_id.
            throw new ProblemException(ProblemType.DUPLICATE_TAX_ID);
        }
    }
}

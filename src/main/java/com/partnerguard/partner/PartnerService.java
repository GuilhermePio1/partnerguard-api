package com.partnerguard.partner;

import com.partnerguard.audit.AuditAction;
import com.partnerguard.audit.AuditService;
import com.partnerguard.common.error.ProblemException;
import com.partnerguard.common.error.ProblemType;
import com.partnerguard.common.error.ResourceNotFoundException;
import com.partnerguard.common.validation.Cnpj;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class PartnerService {

    private final PartnerRepository partners;
    private final AuditService audit;

    PartnerService(PartnerRepository partners, AuditService audit) {
        this.partners = partners;
        this.audit = audit;
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
        Partner partner;
        try {
            partner = partners.saveAndFlush(Partner.create(legalName, normalizedTaxId));
        } catch (DataIntegrityViolationException e) {
            // A concurrent insert won the race on uq_partner_tax_id.
            throw new ProblemException(ProblemType.DUPLICATE_TAX_ID);
        }
        // Same transaction: if the audit insert fails, the partner isn't created (ADR-0012).
        audit.record(AuditAction.PARTNER_CREATED, "partner", partner.getId(), List.of("legalName", "taxId"));
        return partner;
    }
}

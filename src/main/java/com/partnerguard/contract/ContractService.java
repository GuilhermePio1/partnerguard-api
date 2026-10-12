package com.partnerguard.contract;

import com.partnerguard.common.error.ResourceNotFoundException;
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
class ContractService {

    private final ContractRepository contracts;

    ContractService(ContractRepository contracts) {
        this.contracts = contracts;
    }

    public Page<Contract> list(OwnershipScope scope, Pageable pageable) {
        return switch (scope) {
            case OwnershipScope.Unrestricted ignored -> contracts.findAll(pageable);
            case OwnershipScope.PartnerOnly(var partnerId) -> contracts.findAllByPartnerId(partnerId, pageable);
        };
    }

    public Contract get(OwnershipScope scope, UUID id) {
        Optional<Contract> contract = switch (scope) {
            case OwnershipScope.Unrestricted ignored -> contracts.findById(id);
            case OwnershipScope.PartnerOnly(var partnerId) -> contracts.findByIdAndPartnerId(id, partnerId);
        };
        return contract.orElseThrow(ResourceNotFoundException::new);
    }
}

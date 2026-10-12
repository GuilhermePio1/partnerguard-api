package com.partnerguard.contract;

import com.partnerguard.api.model.ContractDto;
import com.partnerguard.api.model.ContractPageDto;
import org.springframework.data.domain.Page;

final class ContractMapper {

    private ContractMapper() {
    }

    static ContractDto toDto(Contract contract) {
        return new ContractDto(contract.getId(), contract.getPartnerId(), contract.getStartDate(),
                contract.getEndDate(), contract.getTerms());
    }

    static ContractPageDto toPage(Page<Contract> page) {
        return new ContractPageDto(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.map(ContractMapper::toDto).getContent());
    }
}

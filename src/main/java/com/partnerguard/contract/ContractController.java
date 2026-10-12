package com.partnerguard.contract;

import com.partnerguard.api.ContractsApi;
import com.partnerguard.api.model.ContractDto;
import com.partnerguard.api.model.ContractPageDto;
import com.partnerguard.common.caller.CallerContext;
import com.partnerguard.common.web.SortOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class ContractController implements ContractsApi {

    private final ContractService contracts;
    private final CallerContext caller;

    ContractController(ContractService contracts, CallerContext caller) {
        this.contracts = contracts;
        this.caller = caller;
    }

    @Override
    public ResponseEntity<ContractPageDto> listContracts(Integer page, Integer size, String sort) {
        return ResponseEntity.ok(ContractMapper.toPage(contracts.list(caller.ownershipScope(),
                SortOptions.pageRequest(page, size, sort, SortOptions.CONTRACTS))));
    }

    @Override
    public ResponseEntity<ContractDto> getContract(UUID id) {
        return ResponseEntity.ok(ContractMapper.toDto(contracts.get(caller.ownershipScope(), id)));
    }
}

package com.partnerguard.contract;

import com.partnerguard.common.error.ResourceNotFoundException;
import com.partnerguard.common.ownership.OwnershipScope;
import com.partnerguard.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static com.partnerguard.support.Fixtures.CONTRACT_A;
import static com.partnerguard.support.Fixtures.CONTRACT_B;
import static com.partnerguard.support.Fixtures.PARTNER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ownership rules at the service level (ADR-0004). HTTP can't exercise the partner scope
 * until authentication exists (milestones 3-4), but the rule lives here, so it's proven here.
 */
@SpringBootTest
class ContractServiceTests extends PostgresIntegrationTest {

    private static final OwnershipScope PARTNER_A_SCOPE = OwnershipScope.partner(PARTNER_A);

    @Autowired
    ContractService contracts;

    @Test
    @DisplayName("CTR-02 / CTR-07 a partner lists only its own contracts, and the total counts only those")
    void partnerListsOwnContracts() {
        Page<Contract> page = contracts.list(PARTNER_A_SCOPE, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Contract::getId).containsExactly(CONTRACT_A);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("CTR-04 a partner reads its own contract")
    void partnerReadsOwnContract() {
        assertThat(contracts.get(PARTNER_A_SCOPE, CONTRACT_A).getId()).isEqualTo(CONTRACT_A);
    }

    @Test
    @DisplayName("CTR-03 / AUTHZ-18 another partner's contract is reported as not found")
    void otherPartnersContractIsNotFound() {
        assertThatThrownBy(() -> contracts.get(PARTNER_A_SCOPE, CONTRACT_B))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

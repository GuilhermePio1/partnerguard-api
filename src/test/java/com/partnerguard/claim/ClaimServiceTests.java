package com.partnerguard.claim;

import com.partnerguard.common.error.ResourceNotFoundException;
import com.partnerguard.common.ownership.OwnershipScope;
import com.partnerguard.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static com.partnerguard.support.Fixtures.CLAIM_A2;
import static com.partnerguard.support.Fixtures.CLAIM_B1;
import static com.partnerguard.support.Fixtures.PARTNER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ownership rules at the service level (ADR-0004). HTTP can't exercise the partner scope
 * until authentication exists (milestones 3-4), but the rule lives here, so it's proven here.
 */
@SpringBootTest
@Transactional
class ClaimServiceTests extends PostgresIntegrationTest {

    private static final OwnershipScope PARTNER_A_SCOPE = OwnershipScope.partner(PARTNER_A);

    @Autowired
    ClaimService claims;

    @Test
    @DisplayName("CLM-01 a partner lists only its own claims")
    void partnerListsOwnClaims() {
        assertThat(claims.list(PARTNER_A_SCOPE, null, PageRequest.of(0, 20)).getContent())
                .extracting(Claim::getId)
                .containsExactlyInAnyOrder(CLAIM_A1, CLAIM_A2);
    }

    @Test
    @DisplayName("CLM-01 the status filter stays inside the partner's scope")
    void statusFilterKeepsScope() {
        assertThat(claims.list(PARTNER_A_SCOPE, ClaimStatus.SUBMITTED, PageRequest.of(0, 20)).getContent())
                .extracting(Claim::getId)
                .containsExactly(CLAIM_A1);
    }

    @Test
    @DisplayName("CLM-03 / AUTHZ-18 another partner's claim is reported as not found")
    void otherPartnersClaimIsNotFound() {
        assertThatThrownBy(() -> claims.get(PARTNER_A_SCOPE, CLAIM_B1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("CLM-14 another partner's claim can't be updated, and stays unchanged")
    void otherPartnersClaimCannotBeUpdated() {
        assertThatThrownBy(() -> claims.update(PARTNER_A_SCOPE, CLAIM_B1, new ClaimChanges(null, "x", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(claims.get(OwnershipScope.unrestricted(), CLAIM_B1).getDescription())
                .isEqualTo("Windshield replacement");
    }

    @Test
    @DisplayName("ADR-0009 the state machine allows only the documented transitions")
    void stateMachine() {
        assertThat(ClaimStatus.SUBMITTED.canTransitionTo(ClaimStatus.UNDER_REVIEW)).isTrue();
        assertThat(ClaimStatus.UNDER_REVIEW.canTransitionTo(ClaimStatus.APPROVED)).isTrue();
        assertThat(ClaimStatus.UNDER_REVIEW.canTransitionTo(ClaimStatus.REJECTED)).isTrue();

        assertThat(ClaimStatus.SUBMITTED.canTransitionTo(ClaimStatus.APPROVED)).isFalse();
        assertThat(ClaimStatus.SUBMITTED.canTransitionTo(ClaimStatus.SUBMITTED)).isFalse();
        assertThat(ClaimStatus.UNDER_REVIEW.canTransitionTo(ClaimStatus.SUBMITTED)).isFalse();
        for (ClaimStatus target : ClaimStatus.values()) {
            assertThat(ClaimStatus.APPROVED.canTransitionTo(target)).isFalse();
            assertThat(ClaimStatus.REJECTED.canTransitionTo(target)).isFalse();
        }
    }
}

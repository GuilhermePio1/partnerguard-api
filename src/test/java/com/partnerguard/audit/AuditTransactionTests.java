package com.partnerguard.audit;

import com.partnerguard.support.PostgresIntegrationTest;
import com.partnerguard.support.SecurityTestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.List;

import static com.partnerguard.support.Fixtures.CLAIM_A1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * specs/audit.md rule 1 with real transactions: these tests don't run inside a test
 * transaction, so what they observe is what the application committed or rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditTransactionTests extends PostgresIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    AuditService auditService;

    private String originalDescription;

    @AfterEach
    void restoreClaim() {
        // In case the rollback under test didn't happen: the database is shared by every test class.
        if (originalDescription != null) {
            jdbc.update("update claim set description = ? where id = ?", originalDescription, CLAIM_A1);
        }
    }

    @Test
    @DisplayName("CLM-23 when the audit insert fails, the claim change is rolled back")
    void failedAuditRollsBackTheChange() {
        originalDescription = description();
        long auditRowsBefore = auditRows();
        doThrow(new DataAccessResourceFailureException("audit insert failed"))
                // The spy sits behind the transactional proxy; stub the spy itself, not the proxy.
                .when(AopTestUtils.<AuditService>getUltimateTargetObject(auditService))
                .record(any(), anyString(), any(), anyList());

        MvcTestResult result = mvc.patch().uri("/api/v1/claims/{id}", CLAIM_A1).with(SecurityTestTokens.admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Must not be committed\"}")
                .exchange();

        assertThat(result).hasStatus(500);
        assertThat(description()).isEqualTo(originalDescription);
        assertThat(auditRows()).isEqualTo(auditRowsBefore);
    }

    @Test
    @DisplayName("AUD-08 called without a transaction, the audit service fails and writes no row")
    void recordNeedsATransaction() {
        long auditRowsBefore = auditRows();

        assertThatThrownBy(() -> auditService.record(AuditAction.CLAIM_UPDATED, "claim", CLAIM_A1, List.of("status")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(auditRows()).isEqualTo(auditRowsBefore);
    }

    private String description() {
        return jdbc.queryForObject("select description from claim where id = ?", String.class, CLAIM_A1);
    }

    private long auditRows() {
        Long count = jdbc.queryForObject("select count(*) from audit_log", Long.class);
        return count == null ? 0 : count;
    }
}

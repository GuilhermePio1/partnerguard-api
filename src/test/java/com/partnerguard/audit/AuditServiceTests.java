package com.partnerguard.audit;

import com.partnerguard.common.caller.CallerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** specs/audit.md model: what one entry holds. */
class AuditServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-13T12:34:56.789Z");
    private static final UUID CLAIM_ID = UUID.fromString("40000000-0000-4000-8000-00000000a001");

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final CallerContext caller = mock(CallerContext.class);
    private final AuditService audit = new AuditService(repository, caller, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("AUD-07 with a fixed Clock, the entry's timestamp is the clock's instant")
    void timestampComesFromTheClock() {
        when(caller.subject()).thenReturn("analyst-subject");

        audit.record(AuditAction.CLAIM_UPDATED, "claim", CLAIM_ID, List.of("status"));

        ArgumentCaptor<AuditEntry> saved = ArgumentCaptor.forClass(AuditEntry.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTimestamp()).isEqualTo(NOW);
        assertThat(saved.getValue().getUserId()).isEqualTo("analyst-subject");
        assertThat(saved.getValue().getResource()).isEqualTo("claim:" + CLAIM_ID);
        assertThat(saved.getValue().getDetails()).isEqualTo(Map.of("fields", List.of("status")));
        assertThat(saved.getValue().getId()).isNotNull();
    }
}

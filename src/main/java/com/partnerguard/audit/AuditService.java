package com.partnerguard.audit;

import com.partnerguard.common.caller.CallerContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Records one audit row per successful write (specs/audit.md, ADR-0012, ADR-0023). Services
 * call it inside their own write transaction, so the row commits or rolls back with the
 * change. {@link Propagation#MANDATORY} makes that structural: called without a
 * transaction, it throws instead of writing a row that could outlive a rolled-back change.
 */
@Service
public class AuditService {

    private final AuditLogRepository entries;
    private final CallerContext caller;
    private final Clock clock;

    AuditService(AuditLogRepository entries, CallerContext caller, Clock clock) {
        this.entries = entries;
        this.caller = caller;
        this.clock = clock;
    }

    /**
     * @param fields names of the fields the request set, never their values
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String resourceType, UUID resourceId, List<String> fields) {
        entries.save(AuditEntry.of(caller.subject(), action, resourceType + ":" + resourceId, fields, clock.instant()));
    }
}

package com.partnerguard.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One row of {@code audit_log}: who did which write to what (specs/audit.md). Never updated. */
@Entity
@Table(name = "audit_log")
class AuditEntry {

    @Id
    private UUID id;

    /** The caller's JWT {@code sub}. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    /** {@code <type>:<uuid>}. */
    @Column(nullable = false)
    private String resource;

    /** {@code {"fields":[...]}}: field names, never values (ADR-0023). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, List<String>> details;

    @Column(nullable = false)
    private Instant timestamp;

    protected AuditEntry() {
    }

    private AuditEntry(UUID id, String userId, AuditAction action, String resource,
                       Map<String, List<String>> details, Instant timestamp) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.resource = resource;
        this.details = details;
        this.timestamp = timestamp;
    }

    static AuditEntry of(String userId, AuditAction action, String resource, List<String> fields, Instant timestamp) {
        return new AuditEntry(UUID.randomUUID(), userId, action, resource, Map.of("fields", List.copyOf(fields)),
                timestamp);
    }

    UUID getId() {
        return id;
    }

    String getUserId() {
        return userId;
    }

    AuditAction getAction() {
        return action;
    }

    String getResource() {
        return resource;
    }

    Map<String, List<String>> getDetails() {
        return details;
    }

    Instant getTimestamp() {
        return timestamp;
    }
}

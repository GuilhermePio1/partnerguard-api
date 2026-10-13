package com.partnerguard.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AuditLogRepository extends JpaRepository<AuditEntry, UUID> {
}

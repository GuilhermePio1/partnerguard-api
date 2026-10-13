package com.partnerguard.partner;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PartnerRepository extends JpaRepository<Partner, UUID> {

    boolean existsByTaxId(String taxId);

    boolean existsByIdAndStatus(UUID id, PartnerStatus status);
}

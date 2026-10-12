package com.partnerguard.claim;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Page<Claim> findAllByStatus(ClaimStatus status, Pageable pageable);

    Page<Claim> findAllByPartnerId(UUID partnerId, Pageable pageable);

    Page<Claim> findAllByPartnerIdAndStatus(UUID partnerId, ClaimStatus status, Pageable pageable);

    Optional<Claim> findByIdAndPartnerId(UUID id, UUID partnerId);
}

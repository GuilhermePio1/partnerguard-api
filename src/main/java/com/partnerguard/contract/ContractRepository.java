package com.partnerguard.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ContractRepository extends JpaRepository<Contract, UUID> {

    Page<Contract> findAllByPartnerId(UUID partnerId, Pageable pageable);

    Optional<Contract> findByIdAndPartnerId(UUID id, UUID partnerId);
}

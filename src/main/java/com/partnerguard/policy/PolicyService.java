package com.partnerguard.policy;

import com.partnerguard.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class PolicyService {

    private final PolicyRepository policies;

    PolicyService(PolicyRepository policies) {
        this.policies = policies;
    }

    public Policy getByNumber(String policyNumber) {
        return policies.findByPolicyNumber(policyNumber).orElseThrow(ResourceNotFoundException::new);
    }
}

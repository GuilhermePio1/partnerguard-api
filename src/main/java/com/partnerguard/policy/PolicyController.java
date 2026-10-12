package com.partnerguard.policy;

import com.partnerguard.api.PoliciesApi;
import com.partnerguard.api.model.PolicyDto;
import com.partnerguard.api.model.PolicyStatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PolicyController implements PoliciesApi {

    private final PolicyService policies;

    PolicyController(PolicyService policies) {
        this.policies = policies;
    }

    @Override
    public ResponseEntity<PolicyDto> getPolicyByNumber(String number) {
        Policy policy = policies.getByNumber(number);
        return ResponseEntity.ok(new PolicyDto(policy.getId(), policy.getPolicyNumber(), policy.getHolderName(),
                policy.getVehiclePlate(), PolicyStatusDto.valueOf(policy.getStatus().name())));
    }
}

package com.partnerguard.claim;

import com.partnerguard.common.caller.Role;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which claim fields each role may change (specs/claims.md rule 3, ADR-0009). Checked before
 * the claim is looked up, so the answer never depends on whether the claim exists or whose
 * it is.
 */
final class ClaimPermissions {

    private static final Map<Role, Set<String>> EDITABLE_FIELDS = Map.of(
            Role.ADMIN, Set.of("status", "description", "amount"),
            Role.ANALYST, Set.of("status"),
            Role.PARTNER, Set.of("description", "amount"));

    private ClaimPermissions() {
    }

    static void check(Role editor, ClaimChanges changes) {
        Set<String> editable = EDITABLE_FIELDS.getOrDefault(editor, Set.of());
        List<String> forbidden = new ArrayList<>();
        if (changes.status() != null && !editable.contains("status")) {
            forbidden.add("status");
        }
        if (changes.description() != null && !editable.contains("description")) {
            forbidden.add("description");
        }
        if (changes.amount() != null && !editable.contains("amount")) {
            forbidden.add("amount");
        }
        if (!forbidden.isEmpty()) {
            throw new AccessDeniedException(editor + " may not change " + String.join(", ", forbidden));
        }
    }
}

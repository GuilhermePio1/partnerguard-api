package com.partnerguard.common.web;

import com.partnerguard.common.error.ProblemException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sort allow-lists for list endpoints. They mirror the {@code *Sort} enums in the contract,
 * which the generator does not enforce (ADR-0007, API-02); a test keeps the two in sync.
 * Only values listed here ever reach a query (ADR-0013).
 */
public final class SortOptions {

    public static final Map<String, Sort> PARTNERS = allowList("legalName");
    public static final Map<String, Sort> CONTRACTS = allowList("startDate", "endDate");
    public static final Map<String, Sort> CLAIMS = allowList("status", "amount");

    private SortOptions() {
    }

    /** Page request for an allowed sort value, or a 400 naming the {@code sort} parameter. */
    public static Pageable pageRequest(int page, int size, String sort, Map<String, Sort> allowed) {
        Sort resolved = allowed.get(sort);
        if (resolved == null) {
            throw ProblemException.invalid("sort", "must be one of " + String.join(", ", allowed.keySet()));
        }
        return PageRequest.of(page, size, resolved);
    }

    private static Map<String, Sort> allowList(String... properties) {
        Map<String, Sort> options = new LinkedHashMap<>();
        for (String property : properties) {
            // id as tie-breaker keeps pages stable when sort values repeat.
            options.put(property + ",asc", Sort.by(Sort.Order.asc(property), Sort.Order.asc("id")));
            options.put(property + ",desc", Sort.by(Sort.Order.desc(property), Sort.Order.asc("id")));
        }
        return Map.copyOf(options);
    }
}

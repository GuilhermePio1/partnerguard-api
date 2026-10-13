package com.partnerguard.common.ratelimit;

/** Whether a request may go on and, if not, how many whole seconds to wait (rule 5). */
record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}

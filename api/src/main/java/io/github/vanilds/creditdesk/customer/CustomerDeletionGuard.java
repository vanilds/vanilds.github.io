package io.github.vanilds.creditdesk.customer;

import java.util.UUID;

/**
 * Asks other modules whether a customer can be deleted. The proposal module implements it, so the customer
 * module does not depend on proposals (dependency inversion: the arrow points from proposal to customer only).
 */
public interface CustomerDeletionGuard {

    /** @return a reason why the customer cannot be deleted, or {@code null} if it can. */
    String blockingReason(UUID customerId);
}

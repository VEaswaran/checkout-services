package com.company.checkout.checkout.repository;

import java.util.Optional;

import com.company.checkout.checkout.domain.Checkout;
import com.company.checkout.checkout.domain.CheckoutId;

/**
 * Persistence port. The service depends on this interface only, so the in-memory adapter can be
 * swapped for JPA/Redis/DynamoDB without touching business code.
 */
public interface CheckoutRepository {

    Checkout save(Checkout checkout);

    Optional<Checkout> findById(CheckoutId id);
}

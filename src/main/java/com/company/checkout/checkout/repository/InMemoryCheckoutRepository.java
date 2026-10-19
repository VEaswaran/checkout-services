package com.company.checkout.checkout.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.company.checkout.checkout.domain.Checkout;
import com.company.checkout.checkout.domain.CheckoutId;
import org.springframework.stereotype.Repository;

/**
 * Template-only adapter so the project runs with zero infrastructure.
 * Replace with a real store (e.g. Spring Data JPA entity + mapper) before going to production.
 */
@Repository
public class InMemoryCheckoutRepository implements CheckoutRepository {

    private final Map<CheckoutId, Checkout> store = new ConcurrentHashMap<>();

    @Override
    public Checkout save(Checkout checkout) {
        store.put(checkout.id(), checkout);
        return checkout;
    }

    @Override
    public Optional<Checkout> findById(CheckoutId id) {
        return Optional.ofNullable(store.get(id));
    }
}

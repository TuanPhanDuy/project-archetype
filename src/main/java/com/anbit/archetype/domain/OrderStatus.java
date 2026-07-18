package com.anbit.archetype.domain;

/** Lifecycle of an {@link Order}. Fulfillment is the asynchronous step. */
public enum OrderStatus {
    CREATED,
    FULFILLING,
    FULFILLED,
    FAILED
}

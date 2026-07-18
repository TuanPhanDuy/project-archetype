package com.anbit.archetype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Records that a client's {@code Idempotency-Key} header was used to create a specific
 * order, together with a hash of the request body that produced it. A retry with the same
 * key and the same body is safe — {@code OrderService} looks this row up and returns the
 * original order instead of creating a duplicate. A retry with the same key but a different
 * body is rejected. Immutable: once written, a row is never updated.
 *
 * <p>Implements {@link Persistable} with {@link #isNew()} hard-coded to {@code true} because
 * the {@code key} id is assigned by application code before {@code save()} is ever called.
 * Without this, Spring Data JPA's default new-vs-existing heuristic sees a non-null id and
 * assumes the row already exists, routing {@code save()}/{@code saveAndFlush()} through
 * {@code EntityManager.merge()} instead of {@code persist()}. Under a genuine race on the
 * same brand-new key, {@code merge()} can silently UPDATE the winner's row instead of
 * failing on the unique-constraint INSERT collision. This entity is insert-once and never
 * updated after creation, so always routing through {@code persist()} is correct and makes
 * the race fail fast with {@code DataIntegrityViolationException}, as intended by the caller
 * in {@code OrderService}.
 */
@Entity
@Table(name = "idempotency_key")
public class IdempotencyKey implements Persistable<String> {

    @Id
    @Column(name = "key", nullable = false, updatable = false)
    private String key;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
        // Required by JPA.
    }

    public IdempotencyKey(String key, String requestHash, UUID orderId, Instant createdAt) {
        this.key = key;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String getId() {
        return key;
    }

    /**
     * Always {@code true}: this entity's id is assigned by application code, never generated
     * by the database, and rows are never updated after creation. Hard-coding {@code isNew()}
     * (rather than deriving it from a stored flag) tells Spring Data to always route
     * {@code save()}/{@code saveAndFlush()} through {@code EntityManager.persist()}, so a
     * genuine duplicate-key race fails fast on the unique-constraint INSERT instead of being
     * silently absorbed by {@code merge()}'s existence-check UPDATE.
     */
    @Override
    public boolean isNew() {
        return true;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

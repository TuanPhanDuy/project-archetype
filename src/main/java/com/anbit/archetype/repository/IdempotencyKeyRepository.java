package com.anbit.archetype.repository;

import com.anbit.archetype.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence boundary for {@link IdempotencyKey}. Insert-only: rows are looked up by key
 * and never updated, so the inherited {@code findById}/{@code save}/{@code saveAndFlush} are
 * all this needs — no custom query methods.
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}

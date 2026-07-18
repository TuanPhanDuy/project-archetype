package com.anbit.archetype.repository;

import com.anbit.archetype.domain.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Loads an order with its items and each item's product in one go (for detail/response). */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findWithItemsById(UUID id);

    /** List view: scalar order fields only — items are intentionally not fetched. */
    Page<Order> findAllBy(Pageable pageable);
}

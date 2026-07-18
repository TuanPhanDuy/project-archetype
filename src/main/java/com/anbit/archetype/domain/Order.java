package com.anbit.archetype.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate root. Owns its {@link OrderItem} children (one-to-many, cascaded with
 * orphan removal) — items have no lifecycle outside their order. The total is derived from
 * the items, never set directly by the client.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    protected Order() {
        // Required by JPA.
    }

    public Order(UUID id, String customerName, Instant createdAt) {
        this.id = id;
        this.customerName = customerName;
        this.status = OrderStatus.CREATED;
        this.totalAmount = BigDecimal.ZERO;
        this.createdAt = createdAt;
    }

    /** Adds an item, wiring the back-reference and recomputing the total. */
    public void addItem(OrderItem item) {
        item.assignTo(this);
        this.items.add(item);
        recalculateTotal();
    }

    public void markFulfilling() {
        this.status = OrderStatus.FULFILLING;
    }

    public void markFulfilled() {
        this.status = OrderStatus.FULFILLED;
    }

    public void markFailed() {
        this.status = OrderStatus.FAILED;
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public long getVersion() {
        return version;
    }
}

package com.onemount.archetype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A line in an {@link Order}: a quantity of one {@link Product} at a captured unit price.
 * The unit price is snapshotted at order time so later product price changes don't
 * retroactively alter historical orders.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    protected OrderItem() {
        // Required by JPA.
    }

    public OrderItem(UUID id, Product product, int quantity) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = product.getPrice();
        this.lineTotal = this.unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void assignTo(Order order) {
        this.order = order;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}

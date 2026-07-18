package com.anbit.archetype.service;

import com.anbit.archetype.common.error.ResourceNotFoundException;
import com.anbit.archetype.domain.Order;
import com.anbit.archetype.domain.OrderItem;
import com.anbit.archetype.domain.Product;
import com.anbit.archetype.dto.CreateOrderRequest;
import com.anbit.archetype.repository.OrderRepository;
import com.anbit.archetype.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronous order business logic: creating an order validates every product, snapshots
 * prices, computes the total, and persists the whole aggregate in a single transaction —
 * the client gets the finished order back immediately (the "sync process" API).
 *
 * <p>The fulfillment state transitions ({@link #markFulfilling}/{@link #markFulfilled}/
 * {@link #markFailed}) are each their own transaction, driven by the asynchronous worker.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository repository;
    private final ProductRepository productRepository;
    private final Clock clock;
    private final Counter ordersCreated;

    public OrderService(OrderRepository repository, ProductRepository productRepository,
                        Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.clock = clock;
        // Custom business metric, exported at /actuator/prometheus as orders_placed_total.
        // Note: avoid a name ending in "created" — Prometheus reserves the _created suffix.
        this.ordersCreated = Counter.builder("orders.placed")
                .description("Number of orders created")
                .register(meterRegistry);
    }

    public Page<Order> findAll(Pageable pageable) {
        return repository.findAllBy(pageable);
    }

    public Order findById(UUID id) {
        return repository.findWithItemsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        Order order = new Order(UUID.randomUUID(), request.customerName(), clock.instant());
        for (CreateOrderRequest.Item item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", item.productId()));
            order.addItem(new OrderItem(UUID.randomUUID(), product, item.quantity()));
        }
        Order saved = repository.save(order);
        ordersCreated.increment();
        return saved;
    }

    @Transactional
    public void markFulfilling(UUID id) {
        Order order = getOrThrow(id);
        order.markFulfilling();
        repository.save(order);
    }

    @Transactional
    public void markFulfilled(UUID id) {
        Order order = getOrThrow(id);
        order.markFulfilled();
        repository.save(order);
    }

    @Transactional
    public void markFailed(UUID id) {
        Order order = getOrThrow(id);
        order.markFailed();
        repository.save(order);
    }

    private Order getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
    }
}

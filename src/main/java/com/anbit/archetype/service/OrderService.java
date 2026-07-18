package com.anbit.archetype.service;

import com.anbit.archetype.common.error.IdempotencyKeyConflictException;
import com.anbit.archetype.common.error.ResourceNotFoundException;
import com.anbit.archetype.domain.IdempotencyKey;
import com.anbit.archetype.domain.Order;
import com.anbit.archetype.domain.OrderItem;
import com.anbit.archetype.domain.Product;
import com.anbit.archetype.dto.CreateOrderRequest;
import com.anbit.archetype.repository.IdempotencyKeyRepository;
import com.anbit.archetype.repository.OrderRepository;
import com.anbit.archetype.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronous order business logic: creating an order validates every product, snapshots
 * prices, computes the total, and persists the whole aggregate in a single transaction —
 * the client gets the finished order back immediately (the "sync process" API).
 *
 * <p>Order creation optionally supports an {@code Idempotency-Key}: replaying the same key
 * with the same request body returns the original order instead of creating a duplicate;
 * replaying it with a different body is rejected with {@link IdempotencyKeyConflictException}.
 *
 * <p>The fulfillment state transitions ({@link #markFulfilling}/{@link #markFulfilled}/
 * {@link #markFailed}) are each their own transaction, driven by the asynchronous worker.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository repository;
    private final ProductRepository productRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final Clock clock;
    private final Counter ordersCreated;

    // Self-injected proxy: internal calls between the transactional methods below must go
    // through Spring's proxy (self.xxx(...)), not plain "this.xxx(...)" — a direct
    // self-invocation bypasses the proxy and silently skips @Transactional. @Lazy breaks the
    // constructor circular-dependency this would otherwise create.
    private final OrderService self;

    public OrderService(OrderRepository repository, ProductRepository productRepository,
                        IdempotencyKeyRepository idempotencyKeyRepository, Clock clock, MeterRegistry meterRegistry,
                        @Lazy OrderService self) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.clock = clock;
        // Custom business metric, exported at /actuator/prometheus as orders_placed_total.
        // Note: avoid a name ending in "created" — Prometheus reserves the _created suffix.
        this.ordersCreated = Counter.builder("orders.placed")
                .description("Number of orders created")
                .register(meterRegistry);
        this.self = self;
    }

    public Page<Order> findAll(Pageable pageable) {
        return repository.findAllBy(pageable);
    }

    public Order findById(UUID id) {
        return repository.findWithItemsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Order", id));
    }

    /**
     * Entry point for order creation. Deliberately NOT wrapped in one transaction: depending
     * on the idempotency outcome, this may (a) persist a new order, (b) persist a new order
     * and then lose a unique-constraint race and have to re-query the winner, or (c) just
     * return an existing order — each of those is its own independent unit of work with its
     * own commit/rollback. A single enclosing transaction would hide the race winner's
     * committed row from this method's own retry read.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order create(CreateOrderRequest request, @Nullable String idempotencyKey) {
        String key = normalize(idempotencyKey);
        if (key == null) {
            return self.persistNewOrder(request);
        }

        String requestHash = hash(request);
        Optional<IdempotencyKey> existing = self.findIdempotencyKey(key);
        if (existing.isPresent()) {
            return resolve(existing.get(), key, requestHash);
        }

        try {
            return self.persistNewOrderWithIdempotencyKey(request, key, requestHash);
        } catch (DataIntegrityViolationException raceLost) {
            // Someone else won the race and committed first; their row is now visible.
            IdempotencyKey winner = self.findIdempotencyKey(key).orElseThrow(() -> raceLost);
            return resolve(winner, key, requestHash);
        }
    }

    /** Builds and persists a new order with no idempotency tracking. Unchanged legacy path. */
    @Transactional
    public Order persistNewOrder(CreateOrderRequest request) {
        Order saved = repository.save(buildOrder(request));
        ordersCreated.increment();
        return saved;
    }

    /**
     * Builds and persists a new order plus its idempotency-key row, atomically. The order is
     * flushed first so the idempotency row's foreign key has something to point at within this
     * same transaction; the counter only increments once the idempotency insert has succeeded,
     * so a caller never sees the metric bumped for an order that got rolled back.
     */
    @Transactional
    public Order persistNewOrderWithIdempotencyKey(CreateOrderRequest request, String key, String requestHash) {
        Order saved = repository.saveAndFlush(buildOrder(request));
        idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(key, requestHash, saved.getId(), clock.instant()));
        ordersCreated.increment();
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> findIdempotencyKey(String key) {
        return idempotencyKeyRepository.findById(key);
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

    /** Resolves a stored idempotency-key row against the current request's hash. */
    private Order resolve(IdempotencyKey existing, String key, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw IdempotencyKeyConflictException.of(key);
        }
        // Fetch live rather than returning a cached/frozen response.
        return self.findById(existing.getOrderId());
    }

    private Order buildOrder(CreateOrderRequest request) {
        Order order = new Order(UUID.randomUUID(), request.customerName(), clock.instant());
        for (CreateOrderRequest.Item item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Product", item.productId()));
            order.addItem(new OrderItem(UUID.randomUUID(), product, item.quantity()));
        }
        return order;
    }

    private static @Nullable String normalize(@Nullable String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    /**
     * SHA-256 hex digest over the customer name and each item's productId/quantity, in order.
     *
     * <p>Each field is length-prefixed ({@code <utf8-byte-length>|<field-bytes>}) rather than
     * joined with fixed delimiters such as {@code '\n'} and {@code ':'}. {@code customerName}
     * has no character-set restriction (only {@code @NotBlank @Size(max = 200)}), so a plain
     * delimiter scheme is ambiguous: a customer name containing a literal {@code '\n'} and
     * {@code ':'} can canonicalize to the exact same bytes as a different name plus an extra
     * item — e.g. {@code customerName = "Ada\n<productId>:2"} with no items collides with
     * {@code customerName = "Ada"} plus an item {@code (<productId>, 2)}. Length-prefixing
     * makes each field's boundary a byte count instead of a scanned character, so no
     * combination of field values can ever produce the same canonical bytes as a different
     * combination (see {@code OrderServiceTest} for the regression case).
     */
    private static String hash(CreateOrderRequest request) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, request.customerName());
        for (CreateOrderRequest.Item item : request.items()) {
            appendField(canonical, item.productId().toString());
            appendField(canonical, String.valueOf(item.quantity()));
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every conforming JDK; this can't happen.
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }

    /**
     * Appends {@code field} to {@code canonical} prefixed with its UTF-8 byte length and a
     * {@code '|'} separator (e.g. {@code "3|Ada"}). The length prefix makes the field's byte
     * boundary explicit, so the field's own content can never be mistaken for a delimiter.
     */
    private static void appendField(StringBuilder canonical, String field) {
        int byteLength = field.getBytes(StandardCharsets.UTF_8).length;
        canonical.append(byteLength).append('|').append(field);
    }
}

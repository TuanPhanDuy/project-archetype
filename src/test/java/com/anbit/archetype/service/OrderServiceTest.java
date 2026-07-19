package com.anbit.archetype.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.anbit.archetype.common.error.IdempotencyKeyConflictException;
import com.anbit.archetype.common.error.ResourceNotFoundException;
import com.anbit.archetype.domain.IdempotencyKey;
import com.anbit.archetype.domain.Order;
import com.anbit.archetype.domain.OrderStatus;
import com.anbit.archetype.domain.Product;
import com.anbit.archetype.dto.CreateOrderRequest;
import com.anbit.archetype.repository.IdempotencyKeyRepository;
import com.anbit.archetype.repository.OrderRepository;
import com.anbit.archetype.repository.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test for synchronous order creation: prices are snapshotted and the total is derived,
 * plus the {@code Idempotency-Key} behaviour (new key, replay, conflict, race-loser retry,
 * blank-key passthrough) using mocked repositories — no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    IdempotencyKeyRepository idempotencyKeyRepository;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    /**
     * Builds a real {@code OrderService} wired to the mocks above. {@code self} is the
     * proxy-routing field used in production to keep {@code @Transactional} boundaries honest
     * ({@code @Lazy} breaks the constructor cycle); there is no Spring proxy in a plain unit
     * test, so we point {@code self} back at the very instance being built via reflection
     * (the field is only assignable through the constructor otherwise).
     */
    private OrderService newService() {
        OrderService service = new OrderService(
                orderRepository,
                productRepository,
                idempotencyKeyRepository,
                fixedClock,
                new SimpleMeterRegistry(),
                null);
        ReflectionTestUtils.setField(service, "self", service);
        return service;
    }

    private static String computeHash(CreateOrderRequest request) {
        try {
            Method hash = OrderService.class.getDeclaredMethod("hash", CreateOrderRequest.class);
            hash.setAccessible(true);
            return (String) hash.invoke(null, request);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** A $10.00 product named "Widget" — the default product fixture used across these tests. */
    private Product widget(UUID id) {
        return new Product(id, "Widget", null, new BigDecimal("10.00"), fixedClock.instant());
    }

    /** A single-item order request: one {@code quantity} of {@code productId}. */
    private static CreateOrderRequest orderRequest(
            String customerName, UUID productId, int quantity) {
        CreateOrderRequest.Item item = new CreateOrderRequest.Item(productId, quantity);
        return new CreateOrderRequest(customerName, List.of(item));
    }

    @Test
    void createComputesTotalFromLineItems() {
        UUID widgetId = UUID.randomUUID();
        UUID gadgetId = UUID.randomUUID();
        Product gadget =
                new Product(gadgetId, "Gadget", null, new BigDecimal("2.50"), fixedClock.instant());
        when(productRepository.findById(widgetId)).thenReturn(Optional.of(widget(widgetId)));
        when(productRepository.findById(gadgetId)).thenReturn(Optional.of(gadget));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = newService().create(new CreateOrderRequest("Ada", List.of(
                new CreateOrderRequest.Item(widgetId, 2),   // 2 * 10.00 = 20.00
                new CreateOrderRequest.Item(gadgetId, 4))), null); // 4 *  2.50 = 10.00

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void createFailsWhenProductMissing() {
        UUID missing = UUID.randomUUID();
        when(productRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().create(orderRequest("Ada", missing, 1), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    // ---- Idempotency-Key: AC1 (new key persists order + key row) ----

    @Test
    void createWithNewIdempotencyKeyPersistsOrderAndIdempotencyRow() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(widget(productId)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeyRepository.findById("new-key")).thenReturn(Optional.empty());
        when(idempotencyKeyRepository.saveAndFlush(any(IdempotencyKey.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest request = orderRequest("Ada", productId, 1);
        Order order = newService().create(request, "new-key");

        assertThat(order.getCustomerName()).isEqualTo("Ada");
        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeyRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("new-key");
        assertThat(captor.getValue().getOrderId()).isEqualTo(order.getId());
        assertThat(captor.getValue().getRequestHash()).isEqualTo(computeHash(request));
        verify(orderRepository).saveAndFlush(any(Order.class));
    }

    // ---- Idempotency-Key: AC2 (replay, same body -> current state, no re-persist) ----

    @Test
    void replayWithSameKeyAndSameBodyReturnsCurrentStateWithoutDuplicatePersist() {
        UUID productId = UUID.randomUUID();
        CreateOrderRequest request = orderRequest("Ada", productId, 1);
        UUID orderId = UUID.randomUUID();
        IdempotencyKey stored = new IdempotencyKey(
                "replay-key", computeHash(request), orderId, fixedClock.instant());
        when(idempotencyKeyRepository.findById("replay-key")).thenReturn(Optional.of(stored));

        // The order's state has moved on since it was created (e.g. the async worker started
        // fulfilling it); the replay must reflect that, not a frozen snapshot from creation time.
        Order current = new Order(orderId, "Ada", fixedClock.instant());
        current.markFulfilling();
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(current));

        Order result = newService().create(request, "replay-key");

        assertThat(result.getId()).isEqualTo(orderId);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.FULFILLING);
        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).save(any());
        verify(idempotencyKeyRepository, never()).saveAndFlush(any());
    }

    // ---- Idempotency-Key: AC3 (replay, different body -> 409-mapped conflict, no persist) ----

    @Test
    void replayWithDifferentBodyThrowsConflictAndDoesNotPersist() {
        UUID productId = UUID.randomUUID();
        CreateOrderRequest original = orderRequest("Ada", productId, 1);
        CreateOrderRequest different = orderRequest("Bella", productId, 5);
        IdempotencyKey stored = new IdempotencyKey(
                "conflict-key", computeHash(original), UUID.randomUUID(), fixedClock.instant());
        when(idempotencyKeyRepository.findById("conflict-key")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> newService().create(different, "conflict-key"))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessageContaining("conflict-key");
        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).findWithItemsById(any());
        verify(idempotencyKeyRepository, never()).saveAndFlush(any());
    }

    // ---- Idempotency-Key: AC4 (race-loser retry path; concurrency itself covered by the IT) ----

    @Test
    void createRetriesAndReturnsRaceWinnerWhenIdempotencyInsertLosesUniqueConstraint() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(widget(productId)));
        CreateOrderRequest request = orderRequest("Ada", productId, 1);
        UUID winnerOrderId = UUID.randomUUID();
        IdempotencyKey winnerKey = new IdempotencyKey(
                "race-key", computeHash(request), winnerOrderId, fixedClock.instant());
        Order winnerOrder = new Order(winnerOrderId, "Ada", fixedClock.instant());

        // First lookup misses (nobody has claimed the key yet); this thread then loses the
        // unique-constraint race on insert, and the retry lookup finds the winner's committed row.
        when(idempotencyKeyRepository.findById("race-key"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerKey));
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint"));
        when(orderRepository.findWithItemsById(winnerOrderId)).thenReturn(Optional.of(winnerOrder));

        Order result = newService().create(request, "race-key");

        assertThat(result.getId()).isEqualTo(winnerOrderId);
        verify(idempotencyKeyRepository, times(2)).findById("race-key");
        verify(idempotencyKeyRepository, never()).saveAndFlush(any());
    }

    // ---- Idempotency-Key: AC5 (blank/whitespace key behaves like no key at all) ----

    @Test
    void blankIdempotencyKeyBehavesLikeNoKey() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.of(widget(productId)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = newService().create(orderRequest("Ada", productId, 1), "   ");

        assertThat(order.getCustomerName()).isEqualTo("Ada");
        verifyNoInteractions(idempotencyKeyRepository);
        verify(orderRepository).save(any(Order.class));
        verify(orderRepository, never()).saveAndFlush(any());
    }

    // ---- Pure algorithm checks: hash determinism and key normalization ----

    @Test
    void hashIsDeterministicForIdenticalRequestsAndDiffersForDifferentOnes() {
        UUID productId = UUID.randomUUID();
        CreateOrderRequest a = orderRequest("Ada", productId, 2);
        CreateOrderRequest sameValues = orderRequest("Ada", productId, 2);
        CreateOrderRequest differentQty = orderRequest("Ada", productId, 3);
        CreateOrderRequest differentName = orderRequest("Bella", productId, 2);

        String hashA = computeHash(a);

        assertThat(hashA).isEqualTo(computeHash(sameValues));
        assertThat(hashA).matches("[0-9a-f]{64}");
        assertThat(hashA).isNotEqualTo(computeHash(differentQty));
        assertThat(hashA).isNotEqualTo(computeHash(differentName));
    }

    /**
     * Regression guard for the delimiter-collision finding: {@code customerName} has no
     * character-set restriction ({@code @NotBlank @Size(max = 200)} only), so before
     * length-prefixing was introduced a customer name that itself contained the {@code '\n'}
     * and {@code ':'} delimiters could canonicalize to the exact same bytes as a different
     * request with an extra item — letting a client's request B be silently treated as a
     * replay of an unrelated request A (defeating AC3's same-key/different-body 409).
     */
    @Test
    void hashDoesNotCollideWhenCustomerNameEmbedsDelimiterCharacters() {
        UUID sharedProductId = UUID.randomUUID();
        UUID embeddedProductId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        // A: a single item whose "quantity" is really baked into the customer name via the
        // old delimiter characters ('\n' and ':').
        CreateOrderRequest requestA = new CreateOrderRequest(
                "Ada\n" + embeddedProductId + ":2",
                List.of(new CreateOrderRequest.Item(sharedProductId, 3)));

        // B: a plain customer name plus two genuinely separate items - one of which reproduces
        // the exact bytes that were smuggled into A's customerName above.
        CreateOrderRequest requestB = new CreateOrderRequest(
                "Ada",
                List.of(
                        new CreateOrderRequest.Item(embeddedProductId, 2),
                        new CreateOrderRequest.Item(sharedProductId, 3)));

        assertThat(computeHash(requestA)).isNotEqualTo(computeHash(requestB));
    }

    @Test
    void normalizeTreatsNullAndBlankAsNoKeyAndTrimsWhitespace() throws Exception {
        Method normalize = OrderService.class.getDeclaredMethod("normalize", String.class);
        normalize.setAccessible(true);

        assertThat(normalize.invoke(null, (Object) null)).isNull();
        assertThat(normalize.invoke(null, "")).isNull();
        assertThat(normalize.invoke(null, "   ")).isNull();
        assertThat(normalize.invoke(null, "  abc-123  ")).isEqualTo("abc-123");
    }
}

package com.onemount.archetype.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.onemount.archetype.common.error.ResourceNotFoundException;
import com.onemount.archetype.domain.Order;
import com.onemount.archetype.domain.OrderStatus;
import com.onemount.archetype.domain.Product;
import com.onemount.archetype.dto.CreateOrderRequest;
import com.onemount.archetype.repository.OrderRepository;
import com.onemount.archetype.repository.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test for synchronous order creation: prices are snapshotted and the total is derived. */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    ProductRepository productRepository;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void createComputesTotalFromLineItems() {
        UUID widgetId = UUID.randomUUID();
        UUID gadgetId = UUID.randomUUID();
        Product widget = new Product(widgetId, "Widget", null, new BigDecimal("10.00"), fixedClock.instant());
        Product gadget = new Product(gadgetId, "Gadget", null, new BigDecimal("2.50"), fixedClock.instant());
        when(productRepository.findById(widgetId)).thenReturn(Optional.of(widget));
        when(productRepository.findById(gadgetId)).thenReturn(Optional.of(gadget));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderService service = new OrderService(orderRepository, productRepository, fixedClock, new SimpleMeterRegistry());
        Order order = service.create(new CreateOrderRequest("Ada", List.of(
                new CreateOrderRequest.Item(widgetId, 2),   // 2 * 10.00 = 20.00
                new CreateOrderRequest.Item(gadgetId, 4)))); // 4 *  2.50 = 10.00

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void createFailsWhenProductMissing() {
        UUID missing = UUID.randomUUID();
        when(productRepository.findById(missing)).thenReturn(Optional.empty());

        OrderService service = new OrderService(orderRepository, productRepository, fixedClock, new SimpleMeterRegistry());

        assertThatThrownBy(() -> service.create(
                new CreateOrderRequest("Ada", List.of(new CreateOrderRequest.Item(missing, 1)))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }
}

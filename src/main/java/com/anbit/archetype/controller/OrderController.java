package com.anbit.archetype.controller;

import com.anbit.archetype.domain.JobType;
import com.anbit.archetype.domain.Order;
import com.anbit.archetype.domain.ProcessingJob;
import com.anbit.archetype.dto.CreateOrderRequest;
import com.anbit.archetype.dto.JobResponse;
import com.anbit.archetype.dto.OrderResponse;
import com.anbit.archetype.dto.OrderSummaryResponse;
import com.anbit.archetype.service.FulfillmentService;
import com.anbit.archetype.service.JobService;
import com.anbit.archetype.service.OrderService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Order endpoints demonstrating the two processing styles:
 *
 * <ul>
 *   <li><b>Synchronous</b> — {@code POST /api/v1/orders} does the work in-request and
 *       returns 201 with the finished order.</li>
 *   <li><b>Asynchronous</b> — {@code POST /api/v1/orders/{id}/fulfillment} starts background
 *       work and returns 202 Accepted immediately, with a {@code Location} pointing at the
 *       job to poll ({@code GET /api/v1/jobs/{jobId}}).</li>
 * </ul>
 */
@Tag(name = "Orders", description = "Synchronous order creation and asynchronous fulfillment")
@Validated
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final FulfillmentService fulfillmentService;
    private final JobService jobService;

    public OrderController(OrderService orderService, FulfillmentService fulfillmentService, JobService jobService) {
        this.orderService = orderService;
        this.fulfillmentService = fulfillmentService;
        this.jobService = jobService;
    }

    @GetMapping
    public Page<OrderSummaryResponse> list(Pageable pageable) {
        return orderService.findAll(pageable).map(OrderSummaryResponse::from);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.findById(id));
    }

    /**
     * Synchronous process: validate, price, persist, and return the finished order.
     *
     * <p>An {@code Idempotency-Key} header makes retries safe: replaying the same key with the
     * same body returns the original order instead of creating a duplicate; replaying it with a
     * different body is rejected with 409.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(
                    description = "Optional client-generated key for safely retrying this exact request. "
                            + "Reusing the same key with the same body returns the original order instead of "
                            + "creating a duplicate; reusing it with a different body is rejected.",
                    example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(max = 255, message = "must be at most 255 characters") @Nullable String idempotencyKey,
            UriComponentsBuilder uriBuilder) {
        Order created = orderService.create(request, idempotencyKey);
        URI location = uriBuilder.path("/api/v1/orders/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(OrderResponse.from(created));
    }

    /**
     * Asynchronous process: acknowledge with 202 and a job to poll. The job row is committed
     * before the background task starts, so the returned id is always pollable.
     */
    @PostMapping("/{id}/fulfillment")
    public ResponseEntity<JobResponse> fulfill(@PathVariable UUID id, UriComponentsBuilder uriBuilder) {
        orderService.findById(id); // 404 fast if the order doesn't exist
        ProcessingJob job = jobService.create(JobType.ORDER_FULFILLMENT, id);
        orderService.markFulfilling(id);
        fulfillmentService.process(job.getId(), id);

        URI jobLocation = uriBuilder.path("/api/v1/jobs/{jobId}").buildAndExpand(job.getId()).toUri();
        return ResponseEntity.accepted().location(jobLocation).body(JobResponse.from(job));
    }
}

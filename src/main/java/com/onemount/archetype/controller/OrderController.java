package com.onemount.archetype.controller;

import com.onemount.archetype.domain.JobType;
import com.onemount.archetype.domain.Order;
import com.onemount.archetype.domain.ProcessingJob;
import com.onemount.archetype.dto.CreateOrderRequest;
import com.onemount.archetype.dto.JobResponse;
import com.onemount.archetype.dto.OrderResponse;
import com.onemount.archetype.dto.OrderSummaryResponse;
import com.onemount.archetype.service.FulfillmentService;
import com.onemount.archetype.service.JobService;
import com.onemount.archetype.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** Synchronous process: validate, price, persist, and return the finished order. */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request, UriComponentsBuilder uriBuilder) {
        Order created = orderService.create(request);
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

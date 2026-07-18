package com.onemount.archetype.service;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronous order fulfillment (the "async process" API). The {@code @Async} method runs
 * on the virtual-thread executor configured in {@code AsyncConfig}, off the request thread,
 * and records progress on the {@link com.onemount.archetype.domain.ProcessingJob} so the caller
 * (who already received a 202 + job id) can poll for the outcome.
 *
 * <p>It lives in a different bean from its caller on purpose: {@code @Async} only takes
 * effect when invoked through the Spring proxy, never via a self-call.
 */
@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    /** Simulated processing time so the async behaviour is observable; short for tests. */
    private static final Duration WORK_DURATION = Duration.ofMillis(300);

    private final OrderService orderService;
    private final JobService jobService;

    public FulfillmentService(OrderService orderService, JobService jobService) {
        this.orderService = orderService;
        this.jobService = jobService;
    }

    @Async("fulfillmentExecutor")
    public void process(UUID jobId, UUID orderId) {
        jobService.markRunning(jobId);
        try {
            // Stand-in for real work: reserve stock, charge payment, notify a warehouse, etc.
            Thread.sleep(WORK_DURATION.toMillis());
            orderService.markFulfilled(orderId);
            jobService.markCompleted(jobId, "Order " + orderId + " fulfilled");
            log.info("Fulfilled order {} (job {})", orderId, jobId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            failQuietly(jobId, orderId, "Fulfillment interrupted");
        } catch (RuntimeException ex) {
            log.error("Fulfillment failed for order {} (job {})", orderId, jobId, ex);
            failQuietly(jobId, orderId, ex.getMessage());
        }
    }

    private void failQuietly(UUID jobId, UUID orderId, String message) {
        try {
            orderService.markFailed(orderId);
        } catch (RuntimeException ignored) {
            // Order may have been deleted; the job still records the failure below.
        }
        jobService.markFailed(jobId, message);
    }
}

package com.onemount.archetype.controller;

import com.onemount.archetype.dto.JobResponse;
import com.onemount.archetype.service.JobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Polling endpoint for asynchronous jobs. Clients that kicked off async work (and got a
 * 202 + Location) poll here until {@code status} is {@code COMPLETED} or {@code FAILED}.
 */
@Tag(name = "Jobs", description = "Poll the status of asynchronous jobs")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return JobResponse.from(service.findById(id));
    }
}

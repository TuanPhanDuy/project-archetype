package com.anbit.archetype.service;

import com.anbit.archetype.common.error.ResourceNotFoundException;
import com.anbit.archetype.domain.JobType;
import com.anbit.archetype.domain.ProcessingJob;
import com.anbit.archetype.repository.JobRepository;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages {@link ProcessingJob} lifecycle. Each state transition runs in its own
 * transaction so that an asynchronous worker's progress is committed and immediately
 * visible to clients polling the job — even while the worker keeps running.
 */
@Service
@Transactional(readOnly = true)
public class JobService {

    private final JobRepository repository;
    private final Clock clock;

    public JobService(JobRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ProcessingJob findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Job", id));
    }

    @Transactional
    public ProcessingJob create(JobType type, @Nullable UUID referenceId) {
        ProcessingJob job =
                new ProcessingJob(UUID.randomUUID(), type, referenceId, clock.instant());
        return repository.save(job);
    }

    @Transactional
    public void markRunning(UUID id) {
        ProcessingJob job = findById(id);
        job.markRunning(clock.instant());
        repository.save(job);
    }

    @Transactional
    public void markCompleted(UUID id, @Nullable String result) {
        ProcessingJob job = findById(id);
        job.markCompleted(result, clock.instant());
        repository.save(job);
    }

    @Transactional
    public void markFailed(UUID id, @Nullable String error) {
        ProcessingJob job = findById(id);
        job.markFailed(error, clock.instant());
        repository.save(job);
    }
}

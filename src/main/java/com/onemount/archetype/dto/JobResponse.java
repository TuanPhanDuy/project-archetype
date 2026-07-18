package com.onemount.archetype.dto;

import com.onemount.archetype.domain.JobStatus;
import com.onemount.archetype.domain.JobType;
import com.onemount.archetype.domain.ProcessingJob;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Status view of an async job, returned by the 202 response and the polling endpoint. */
public record JobResponse(
        UUID id,
        JobType type,
        JobStatus status,
        @Nullable UUID referenceId,
        @Nullable String result,
        @Nullable String error,
        Instant createdAt,
        Instant updatedAt) {

    public static JobResponse from(ProcessingJob job) {
        return new JobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getReferenceId(),
                job.getResult(),
                job.getError(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}

package com.anbit.archetype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Tracks a unit of asynchronous work. The async API returns a job id; clients poll the
 * job to learn when the work has {@code COMPLETED} or {@code FAILED}. This persisted record
 * is what makes async results durable and observable across requests/instances.
 */
@Entity
@Table(name = "processing_job")
public class ProcessingJob {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "reference_id")
    private @Nullable UUID referenceId;

    @Column
    private @Nullable String result;

    @Column
    private @Nullable String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProcessingJob() {
        // Required by JPA.
    }

    public ProcessingJob(UUID id, JobType type, @Nullable UUID referenceId, Instant now) {
        this.id = id;
        this.type = type;
        this.referenceId = referenceId;
        this.status = JobStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markRunning(Instant now) {
        this.status = JobStatus.RUNNING;
        this.updatedAt = now;
    }

    public void markCompleted(@Nullable String result, Instant now) {
        this.status = JobStatus.COMPLETED;
        this.result = result;
        this.updatedAt = now;
    }

    public void markFailed(@Nullable String error, Instant now) {
        this.status = JobStatus.FAILED;
        this.error = error;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public JobType getType() {
        return type;
    }

    public JobStatus getStatus() {
        return status;
    }

    public @Nullable UUID getReferenceId() {
        return referenceId;
    }

    public @Nullable String getResult() {
        return result;
    }

    public @Nullable String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}

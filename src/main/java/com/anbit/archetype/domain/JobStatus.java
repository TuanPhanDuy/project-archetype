package com.anbit.archetype.domain;

/** Lifecycle of an asynchronous {@link ProcessingJob}. */
public enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

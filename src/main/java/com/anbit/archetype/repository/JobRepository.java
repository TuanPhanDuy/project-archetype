package com.anbit.archetype.repository;

import com.anbit.archetype.domain.ProcessingJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<ProcessingJob, UUID> {
}

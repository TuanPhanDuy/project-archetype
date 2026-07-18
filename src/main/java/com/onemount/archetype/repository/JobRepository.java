package com.onemount.archetype.repository;

import com.onemount.archetype.domain.ProcessingJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<ProcessingJob, UUID> {
}

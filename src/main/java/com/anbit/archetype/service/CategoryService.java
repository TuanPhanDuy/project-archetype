package com.anbit.archetype.service;

import com.anbit.archetype.common.error.ResourceNotFoundException;
import com.anbit.archetype.domain.Category;
import com.anbit.archetype.dto.CategoryRequest;
import com.anbit.archetype.repository.CategoryRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository repository;
    private final Clock clock;

    public CategoryService(CategoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Page<Category> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Category findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }

    @Transactional
    public Category create(CategoryRequest request) {
        Category category = new Category(
                UUID.randomUUID(), request.name(), request.description(), clock.instant());
        return repository.save(category);
    }
}

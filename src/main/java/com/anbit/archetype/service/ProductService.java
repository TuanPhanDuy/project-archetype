package com.anbit.archetype.service;

import com.anbit.archetype.common.error.ResourceNotFoundException;
import com.anbit.archetype.domain.Category;
import com.anbit.archetype.domain.Product;
import com.anbit.archetype.dto.ProductRequest;
import com.anbit.archetype.repository.CategoryRepository;
import com.anbit.archetype.repository.ProductRepository;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for products. Transaction boundaries live here, not in controllers or
 * repositories. Reads are marked {@code readOnly} so the persistence provider can optimise.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository repository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public ProductService(
            ProductRepository repository, CategoryRepository categoryRepository, Clock clock) {
        this.repository = repository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    public Page<Product> findAll(Pageable pageable) {
        return repository.findAllBy(pageable);
    }

    public Product findById(UUID id) {
        return repository.findWithCategoryById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    @Transactional
    public Product create(ProductRequest request) {
        Product product = new Product(
                UUID.randomUUID(),
                request.name(),
                request.description(),
                request.price(),
                clock.instant());
        product.setCategory(resolveCategory(request.categoryId()));
        return repository.save(product);
    }

    @Transactional
    public Product update(UUID id, ProductRequest request) {
        Product product = findById(id);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(resolveCategory(request.categoryId()));
        return repository.save(product);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw ResourceNotFoundException.of("Product", id);
        }
        repository.deleteById(id);
    }

    private @Nullable Category resolveCategory(@Nullable UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", categoryId));
    }
}

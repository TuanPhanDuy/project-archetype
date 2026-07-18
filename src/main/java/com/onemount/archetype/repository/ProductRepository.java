package com.onemount.archetype.repository;

import com.onemount.archetype.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence boundary for {@link Product}. Keep query methods here; never expose the
 * repository above the service layer.
 *
 * <p>The {@code @EntityGraph} finders eagerly fetch {@code category} so responses can be
 * mapped after the transaction closes (open-in-view is disabled) without tripping a
 * {@code LazyInitializationException}.
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(UUID id);

    @EntityGraph(attributePaths = "category")
    Page<Product> findAllBy(Pageable pageable);
}

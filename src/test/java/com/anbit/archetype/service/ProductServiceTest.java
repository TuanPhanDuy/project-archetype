package com.anbit.archetype.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.anbit.archetype.common.error.ResourceNotFoundException;
import com.anbit.archetype.domain.Product;
import com.anbit.archetype.dto.ProductRequest;
import com.anbit.archetype.repository.CategoryRepository;
import com.anbit.archetype.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit test of the service: no Spring context, no database. Fast feedback for
 * business rules. The {@link Clock} is fixed so timestamps are deterministic.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository repository;

    @Mock
    CategoryRepository categoryRepository;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void createPersistsProductWithGeneratedId() {
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductService service = new ProductService(repository, categoryRepository, fixedClock);
        ProductRequest request = new ProductRequest("Gadget", null, new BigDecimal("5.00"), null);

        Product result = service.create(request);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("Gadget");
        assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findWithCategoryById(id)).thenReturn(Optional.empty());
        ProductService service = new ProductService(repository, categoryRepository, fixedClock);

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}

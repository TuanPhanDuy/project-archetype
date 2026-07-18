package com.anbit.archetype.controller;

import com.anbit.archetype.domain.Category;
import com.anbit.archetype.dto.CategoryRequest;
import com.anbit.archetype.dto.CategoryResponse;
import com.anbit.archetype.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Categories", description = "Catalog categories that products belong to")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CategoryResponse> list(Pageable pageable) {
        return service.findAll(pageable).map(CategoryResponse::from);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return CategoryResponse.from(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest request, UriComponentsBuilder uriBuilder) {
        Category created = service.create(request);
        URI location = uriBuilder.path("/api/v1/categories/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(CategoryResponse.from(created));
    }
}

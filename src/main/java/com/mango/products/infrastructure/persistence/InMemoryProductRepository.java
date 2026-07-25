package com.mango.products.infrastructure.persistence;

import com.mango.products.domain.model.Product;
import com.mango.products.domain.repository.ProductRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory adapter: no external database is needed for this use case, and skipping
 * one keeps startup time and resource usage low. */
public class InMemoryProductRepository implements ProductRepository {

    private final ConcurrentHashMap<Long, Product> productsById = new ConcurrentHashMap<>();

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(productsById.get(id));
    }

    @Override
    public void save(Product product) {
        productsById.put(product.id(), product);
    }
}

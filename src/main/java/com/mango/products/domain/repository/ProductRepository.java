package com.mango.products.domain.repository;

import com.mango.products.domain.model.Product;

import java.util.Optional;

/** Port: persistence contract for {@link Product}, independent of the storage technology used. */
public interface ProductRepository {

    Optional<Product> findById(Long id);

    void save(Product product);
}

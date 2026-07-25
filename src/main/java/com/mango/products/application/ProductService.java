package com.mango.products.application;

import com.mango.products.domain.exception.PriceNotFoundForDateException;
import com.mango.products.domain.exception.ProductNotFoundException;
import com.mango.products.domain.idgen.IdGenerator;
import com.mango.products.domain.model.Price;
import com.mango.products.domain.model.Product;
import com.mango.products.domain.repository.ProductRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Use cases for products and their price history. Kept as a single concrete class
 * (no interface): there is only one real implementation and it is exercised directly
 * by the integration tests, so an interface would add indirection without benefit.
 */
public class ProductService {

    private final ProductRepository productRepository;
    private final IdGenerator idGenerator;

    public ProductService(ProductRepository productRepository, IdGenerator idGenerator) {
        this.productRepository = productRepository;
        this.idGenerator = idGenerator;
    }

    public Product createProduct(String name, String description) {
        Product product = Product.create(idGenerator.next(), name, description);
        productRepository.save(product);
        return product;
    }

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public Price addPrice(Long productId, BigDecimal value, LocalDate initDate, LocalDate endDate) {
        Product product = getProduct(productId);
        Price price = Price.create(value, initDate, endDate);
        product.addPrice(price);
        return price;
    }

    public Price getPriceAt(Long productId, LocalDate date) {
        Product product = getProduct(productId);
        return product.findPriceAt(date)
                .orElseThrow(() -> new PriceNotFoundForDateException(productId, date));
    }
}

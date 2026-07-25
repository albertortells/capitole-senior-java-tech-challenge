package com.mango.products.infrastructure.persistence;

import com.mango.products.domain.idgen.IdGenerator;
import com.mango.products.domain.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryProductRepositoryTest {

    private final InMemoryProductRepository repository = new InMemoryProductRepository();

    @Test
    void savesAndRetrievesAProductById() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");

        repository.save(product);

        assertThat(repository.findById(1L)).contains(product);
    }

    @Test
    void returnsEmptyForAnUnknownId() {
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void survivesConcurrentInsertionOfManyProductsWithoutLosses() throws InterruptedException {
        int count = 1000;
        IdGenerator idGenerator = new AtomicLongIdGenerator();
        Set<Long> generatedIds = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    Long id = idGenerator.next();
                    generatedIds.add(id);
                    repository.save(Product.create(id, "Producto " + id, "Descripcion " + id));
                    return null;
                }));
            }

            ready.await();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (ExecutionException e) {
            throw new AssertionError("Unexpected failure in concurrent task", e);
        }

        assertThat(generatedIds).hasSize(count);
        for (Long id : generatedIds) {
            assertThat(repository.findById(id)).isPresent();
        }
    }
}

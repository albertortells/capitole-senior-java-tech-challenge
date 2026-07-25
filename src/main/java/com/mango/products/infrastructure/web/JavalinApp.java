package com.mango.products.infrastructure.web;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mango.products.application.ProductService;
import com.mango.products.infrastructure.web.controller.HealthController;
import com.mango.products.infrastructure.web.controller.PriceController;
import com.mango.products.infrastructure.web.controller.ProductController;
import com.mango.products.infrastructure.web.exception.GlobalExceptionHandler;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

/**
 * Wires the HTTP adapter: routing, JSON mapping and exception handling. Kept separate
 * from {@code ProductsApplication} so integration tests can start/stop a real server on
 * a random port without going through the process entry point.
 */
public final class JavalinApp {

    private final Javalin javalin;

    public JavalinApp(ProductService productService) {
        ProductController productController = new ProductController(productService);
        PriceController priceController = new PriceController(productService);
        HealthController healthController = new HealthController();

        this.javalin = Javalin.create(config -> {
            // With a 1-CPU container limit, virtual threads don't parallelize more
            // compute; they matter here because they avoid a platform-thread pool
            // (~1MB stack each) eating into the 1GB memory limit under the thousands
            // of concurrent connections the benchmark opens.
            config.useVirtualThreads = true;
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                // Without this, LocalDate serializes as a [year,month,day] array
                // instead of the "yyyy-MM-dd" string the API contract requires.
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }));
        });

        javalin.post("/products", productController::create);
        javalin.post("/products/{id}/prices", priceController::add);
        javalin.get("/products/{id}/prices", priceController::get);
        javalin.get("/actuator/health", healthController::health);

        GlobalExceptionHandler.register(javalin);
    }

    public JavalinApp start(int port) {
        javalin.start(port);
        return this;
    }

    public void stop() {
        javalin.stop();
    }

    public int port() {
        return javalin.port();
    }
}

package com.mango.products;

import com.mango.products.application.ProductService;
import com.mango.products.domain.idgen.IdGenerator;
import com.mango.products.domain.repository.ProductRepository;
import com.mango.products.infrastructure.persistence.AtomicLongIdGenerator;
import com.mango.products.infrastructure.persistence.InMemoryProductRepository;
import com.mango.products.infrastructure.web.JavalinApp;

/** Composition root: wires the concrete adapters into the use cases and starts the HTTP server. */
public class ProductsApplication {

	private static final int PORT = 8080;

	public static void main(String[] args) {
		ProductRepository productRepository = new InMemoryProductRepository();
		IdGenerator idGenerator = new AtomicLongIdGenerator();
		ProductService productService = new ProductService(productRepository, idGenerator);

		new JavalinApp(productService).start(PORT);
	}

}

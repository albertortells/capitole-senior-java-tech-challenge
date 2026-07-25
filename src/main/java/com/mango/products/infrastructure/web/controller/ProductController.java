package com.mango.products.infrastructure.web.controller;

import com.mango.products.application.ProductService;
import com.mango.products.domain.model.Product;
import com.mango.products.infrastructure.web.dto.request.CreateProductRequest;
import com.mango.products.infrastructure.web.mapper.ProductMapper;
import io.javalin.http.Context;

public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    public void create(Context ctx) {
        CreateProductRequest request = ctx.bodyAsClass(CreateProductRequest.class);
        Product product = productService.createProduct(request.name(), request.description());
        ctx.status(201);
        ctx.json(ProductMapper.toResponse(product));
    }
}

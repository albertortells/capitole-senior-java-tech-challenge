package com.mango.products.infrastructure.web.controller;

import com.mango.products.application.ProductService;
import com.mango.products.domain.exception.InvalidRequestException;
import com.mango.products.domain.model.Price;
import com.mango.products.domain.model.Product;
import com.mango.products.infrastructure.web.dto.request.AddPriceRequest;
import com.mango.products.infrastructure.web.mapper.PriceMapper;
import com.mango.products.infrastructure.web.mapper.ProductMapper;
import io.javalin.http.Context;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class PriceController {

    private final ProductService productService;

    public PriceController(ProductService productService) {
        this.productService = productService;
    }

    public void add(Context ctx) {
        Long productId = ctx.pathParamAsClass("id", Long.class).get();
        AddPriceRequest request = ctx.bodyAsClass(AddPriceRequest.class);

        Price price = productService.addPrice(productId, request.value(), request.initDate(), request.endDate());

        ctx.status(201);
        ctx.json(PriceMapper.toItemResponse(price));
    }

    /** Returns the price in effect on {@code ?date=} when present, otherwise the full history. */
    public void get(Context ctx) {
        Long productId = ctx.pathParamAsClass("id", Long.class).get();
        String dateParam = ctx.queryParam("date");

        if (dateParam == null) {
            Product product = productService.getProduct(productId);
            ctx.json(ProductMapper.toHistoryResponse(product));
            return;
        }

        Price price = productService.getPriceAt(productId, parseDate(dateParam));
        ctx.json(PriceMapper.toValueResponse(price));
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Invalid date format, expected yyyy-MM-dd: " + value);
        }
    }
}

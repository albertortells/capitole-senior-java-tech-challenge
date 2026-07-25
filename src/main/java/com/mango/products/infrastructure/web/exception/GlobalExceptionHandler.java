package com.mango.products.infrastructure.web.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mango.products.domain.exception.InvalidRequestException;
import com.mango.products.domain.exception.PriceNotFoundForDateException;
import com.mango.products.domain.exception.PriceOverlapException;
import com.mango.products.domain.exception.ProductNotFoundException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.validation.ValidationException;
import com.mango.products.infrastructure.web.dto.response.ErrorResponse;

import java.util.stream.Collectors;

/** Translates domain/framework exceptions into a single, consistent JSON error shape. */
public final class GlobalExceptionHandler {

    private GlobalExceptionHandler() {
    }

    public static void register(Javalin app) {
        app.exception(ProductNotFoundException.class, (e, ctx) -> respond(ctx, 404, "Not Found", e.getMessage()));
        app.exception(PriceNotFoundForDateException.class, (e, ctx) -> respond(ctx, 404, "Not Found", e.getMessage()));
        app.exception(PriceOverlapException.class, (e, ctx) -> respond(ctx, 409, "Conflict", e.getMessage()));
        app.exception(InvalidRequestException.class, (e, ctx) -> respond(ctx, 400, "Bad Request", e.getMessage()));
        app.exception(ValidationException.class, (e, ctx) -> respond(ctx, 400, "Bad Request", describe(e)));
        app.exception(JsonProcessingException.class, (e, ctx) -> respond(ctx, 400, "Bad Request", "Malformed JSON body"));
        app.exception(Exception.class, (e, ctx) -> respond(ctx, 500, "Internal Server Error", "Unexpected error"));
    }

    private static String describe(ValidationException e) {
        return e.getErrors().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    private static void respond(Context ctx, int status, String error, String message) {
        ctx.status(status);
        ctx.json(new ErrorResponse(status, error, message));
    }
}

package com.mango.products.infrastructure.web.controller;

import io.javalin.http.Context;

import java.util.Map;

/** Manual equivalent of Spring Boot Actuator's /actuator/health, kept minimal on purpose:
 * there is no framework-provided health machinery to plug into since we deliberately
 * did not pull in Spring Boot (see README for the performance rationale). */
public class HealthController {

    public void health(Context ctx) {
        ctx.json(Map.of("status", "UP"));
    }
}

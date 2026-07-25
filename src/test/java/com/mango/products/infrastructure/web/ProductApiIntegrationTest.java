package com.mango.products.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mango.products.application.ProductService;
import com.mango.products.infrastructure.persistence.AtomicLongIdGenerator;
import com.mango.products.infrastructure.persistence.InMemoryProductRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Black-box tests: a real Javalin server is started on a random port and exercised
 * through plain HTTP, exactly like the automated test suite and benchmark.sh will. */
class ProductApiIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JavalinApp app;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void startServer() {
        ProductService productService = new ProductService(new InMemoryProductRepository(), new AtomicLongIdGenerator());
        app = new JavalinApp(productService).start(0);
        baseUrl = "http://localhost:" + app.port();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private long createProduct() throws Exception {
        HttpResponse<String> response = post("/products", "{\"name\":\"Zapatillas\",\"description\":\"Modelo 2025\"}");
        return JSON.readTree(response.body()).get("id").asLong();
    }

    @Test
    void createsAProductAndReturns201WithId() throws Exception {
        HttpResponse<String> response = post("/products", "{\"name\":\"Zapatillas\",\"description\":\"Modelo 2025\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("name").asText()).isEqualTo("Zapatillas");
        assertThat(body.get("description").asText()).isEqualTo("Modelo 2025");
    }

    @Test
    void addsAPriceSuccessfully() throws Exception {
        long id = createProduct();

        HttpResponse<String> response = post("/products/" + id + "/prices",
                "{\"value\":99.99,\"initDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("value").asDouble()).isEqualTo(99.99);
        assertThat(body.get("initDate").asText()).isEqualTo("2024-01-01");
        assertThat(body.get("endDate").asText()).isEqualTo("2024-06-30");
    }

    @Test
    void rejectsAnOverlappingPriceWith409() throws Exception {
        long id = createProduct();
        post("/products/" + id + "/prices", "{\"value\":10,\"initDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}");

        HttpResponse<String> response = post("/products/" + id + "/prices",
                "{\"value\":20,\"initDate\":\"2024-03-01\",\"endDate\":\"2024-12-31\"}");

        assertThat(response.statusCode()).isEqualTo(409);
    }

    @Test
    void rejectsInitDateNotBeforeEndDateWith400() throws Exception {
        long id = createProduct();

        HttpResponse<String> response = post("/products/" + id + "/prices",
                "{\"value\":10,\"initDate\":\"2030-01-01\",\"endDate\":\"2029-01-01\"}");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void returns404WhenAddingAPriceToAnUnknownProduct() throws Exception {
        HttpResponse<String> response = post("/products/999999/prices",
                "{\"value\":10,\"initDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void returnsTheEffectivePriceForADateWithAnExistingPrice() throws Exception {
        long id = createProduct();
        post("/products/" + id + "/prices", "{\"value\":99.99,\"initDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}");

        HttpResponse<String> response = get("/products/" + id + "/prices?date=2024-04-15");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("value").asDouble()).isEqualTo(99.99);
    }

    @Test
    void returns404WhenNoPriceIsInEffectOnTheGivenDate() throws Exception {
        long id = createProduct();
        post("/products/" + id + "/prices", "{\"value\":99.99,\"initDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}");

        HttpResponse<String> response = get("/products/" + id + "/prices?date=2023-01-01");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void returns404ForAnUnknownProductOnAnyOfTheThreeEndpoints() throws Exception {
        assertThat(get("/products/999999/prices").statusCode()).isEqualTo(404);
        assertThat(get("/products/999999/prices?date=2024-01-01").statusCode()).isEqualTo(404);
    }

    @Test
    void anOpenEndedPriceSerializesEndDateAsExplicitNull() throws Exception {
        long id = createProduct();

        HttpResponse<String> response = post("/products/" + id + "/prices",
                "{\"value\":199.99,\"initDate\":\"2025-01-01\",\"endDate\":null}");

        JsonNode body = JSON.readTree(response.body());
        assertThat(body.has("endDate")).isTrue();
        assertThat(body.get("endDate").isNull()).isTrue();
    }

    @Test
    void returnsTheFullHistoryOrderedChronologically() throws Exception {
        long id = createProduct();
        post("/products/" + id + "/prices", "{\"value\":129.99,\"initDate\":\"2024-07-01\",\"endDate\":\"2024-12-31\"}");
        post("/products/" + id + "/prices", "{\"value\":99.99,\"initDate\":\"2024-01-01\",\"endDate\":\"2024-06-30\"}");

        HttpResponse<String> response = get("/products/" + id + "/prices");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("name").asText()).isEqualTo("Zapatillas");
        JsonNode prices = body.get("prices");
        assertThat(prices).hasSize(2);
        assertThat(prices.get(0).get("initDate").asText()).isEqualTo("2024-01-01");
        assertThat(prices.get(1).get("initDate").asText()).isEqualTo("2024-07-01");
    }

    @Test
    void returns400ForMalformedJsonBody() throws Exception {
        HttpResponse<String> response = post("/products", "{not-json");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void returns400ForAnInvalidDateQueryParam() throws Exception {
        long id = createProduct();

        HttpResponse<String> response = get("/products/" + id + "/prices?date=not-a-date");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void returns400ForANonNumericProductId() throws Exception {
        HttpResponse<String> response = get("/products/abc/prices");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("status").asText()).isEqualTo("UP");
    }
}

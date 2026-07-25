package com.mango.products.application;

import com.mango.products.domain.exception.PriceNotFoundForDateException;
import com.mango.products.domain.exception.PriceOverlapException;
import com.mango.products.domain.exception.ProductNotFoundException;
import com.mango.products.domain.idgen.IdGenerator;
import com.mango.products.domain.model.Price;
import com.mango.products.domain.model.Product;
import com.mango.products.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final LocalDate JAN_1 = LocalDate.of(2024, 1, 1);
    private static final LocalDate JUN_30 = LocalDate.of(2024, 6, 30);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private IdGenerator idGenerator;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, idGenerator);
    }

    @Test
    void createProductUsesTheIdGeneratorAndPersistsTheProduct() {
        when(idGenerator.next()).thenReturn(42L);

        Product product = productService.createProduct("Zapatillas", "Modelo 2025");

        assertThat(product.id()).isEqualTo(42L);
        assertThat(product.name()).isEqualTo("Zapatillas");
        verify(productRepository).save(product);
    }

    @Test
    void getProductThrowsWhenTheRepositoryHasNoMatch() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(1L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getProductReturnsWhatTheRepositoryHolds() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThat(productService.getProduct(1L)).isSameAs(product);
    }

    @Test
    void addPriceThrowsProductNotFoundWithoutTouchingTheRepositorySave() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.addPrice(1L, new BigDecimal("10"), JAN_1, JUN_30))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void addPriceStoresTheNewPriceOnTheExistingProduct() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Price price = productService.addPrice(1L, new BigDecimal("99.99"), JAN_1, JUN_30);

        assertThat(product.history()).containsExactly(price);
    }

    @Test
    void addPricePropagatesTheDomainOverlapExceptionUnwrapped() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");
        product.addPrice(Price.create(new BigDecimal("10"), JAN_1, JUN_30));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.addPrice(1L, new BigDecimal("20"), JAN_1, JUN_30))
                .isInstanceOf(PriceOverlapException.class);
    }

    @Test
    void getPriceAtThrowsProductNotFoundWhenTheProductDoesNotExist() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getPriceAt(1L, JAN_1))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getPriceAtThrowsWhenNoPriceIsInEffectOnThatDate() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.getPriceAt(1L, JAN_1))
                .isInstanceOf(PriceNotFoundForDateException.class);
    }

    @Test
    void getPriceAtReturnsTheEffectivePrice() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");
        product.addPrice(Price.create(new BigDecimal("99.99"), JAN_1, JUN_30));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Price price = productService.getPriceAt(1L, LocalDate.of(2024, 3, 1));

        assertThat(price.value()).isEqualByComparingTo("99.99");
    }
}

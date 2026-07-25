package com.mango.products.domain.model;

import com.mango.products.domain.exception.PriceOverlapException;
import com.mango.products.support.PriceTestDataBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mango.products.support.PriceTestDataBuilder.aPrice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static final LocalDate JAN_1 = LocalDate.of(2024, 1, 1);
    private static final LocalDate JUN_30 = LocalDate.of(2024, 6, 30);
    private static final LocalDate JUL_1 = LocalDate.of(2024, 7, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2024, 12, 31);

    private final Product product = Product.create(1L, "Zapatillas deportivas", "Modelo 2025");

    @Test
    void addsANonOverlappingPrice() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        assertThat(product.history()).hasSize(1);
    }

    @Test
    void rejectsAnOverlappingPriceAndKeepsPreviousStateIntact() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        assertThatThrownBy(() -> product.addPrice(aPrice().from(LocalDate.of(2024, 3, 1)).to(DEC_31).build()))
                .isInstanceOf(PriceOverlapException.class);

        assertThat(product.history()).hasSize(1);
    }

    @Test
    void rejectsAPriceSharingExactlyOneBoundaryDayWithAnExistingOne() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        assertThatThrownBy(() -> product.addPrice(aPrice().from(JUN_30).to(DEC_31).build()))
                .isInstanceOf(PriceOverlapException.class);
    }

    @Test
    void acceptsAdjacentPricesWithNoSharedDay() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());
        product.addPrice(aPrice().from(JUL_1).to(DEC_31).build());

        assertThat(product.history()).hasSize(2);
    }

    @Test
    void rejectsAnOpenEndedPriceThatOverlapsAnExistingClosedOne() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        assertThatThrownBy(() -> product.addPrice(aPrice().from(LocalDate.of(2024, 3, 1)).openEnded().build()))
                .isInstanceOf(PriceOverlapException.class);
    }

    @Test
    void acceptsAnOpenEndedPriceStartingRightAfterTheLastClosedOneEnds() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());
        product.addPrice(aPrice().from(JUL_1).openEnded().build());

        assertThat(product.history()).hasSize(2);
    }

    @Test
    void findPriceAtReturnsEmptyWhenThereIsNoPriceYet() {
        assertThat(product.findPriceAt(JAN_1)).isEmpty();
    }

    @Test
    void findPriceAtMatchesOnBothInclusiveBoundaries() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        assertThat(product.findPriceAt(JAN_1)).isPresent();
        assertThat(product.findPriceAt(JUN_30)).isPresent();
    }

    @Test
    void findPriceAtReturnsEmptyForADateBeforeTheFirstPrice() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        assertThat(product.findPriceAt(JAN_1.minusDays(1))).isEmpty();
    }

    @Test
    void findPriceAtReturnsEmptyForAGapBetweenTwoPrices() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());
        product.addPrice(aPrice().from(LocalDate.of(2024, 8, 1)).to(DEC_31).build());

        assertThat(product.findPriceAt(JUL_1)).isEmpty();
    }

    @Test
    void findPriceAtReturnsEmptyAfterTheLastClosedPriceWithNoOpenEndedFollowUp() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        assertThat(product.findPriceAt(DEC_31)).isEmpty();
    }

    @Test
    void findPriceAtMatchesAnOpenEndedPriceIndefinitely() {
        product.addPrice(aPrice().from(JAN_1).openEnded().build());

        assertThat(product.findPriceAt(LocalDate.of(2099, 1, 1))).isPresent();
    }

    @Test
    void historyIsOrderedChronologicallyRegardlessOfInsertionOrder() {
        product.addPrice(aPrice().from(JUL_1).to(DEC_31).build());
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        List<Price> history = product.history();

        assertThat(history).hasSize(2);
        assertThat(history.get(0).range().initDate()).isEqualTo(JAN_1);
        assertThat(history.get(1).range().initDate()).isEqualTo(JUL_1);
    }

    @Test
    void historyIsASnapshotUnaffectedByLaterAdditions() {
        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());

        List<Price> snapshot = product.history();
        product.addPrice(aPrice().from(JUL_1).to(DEC_31).build());

        assertThat(snapshot).hasSize(1);
    }

    @Test
    void onlyOneConcurrentInsertionSucceedsWhenAllRangesOverlap() throws InterruptedException {
        int attempts = 50;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        product.addPrice(aPrice().from(JAN_1).to(JUN_30).build());
                        succeeded.incrementAndGet();
                    } catch (PriceOverlapException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = tasks.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError("Unexpected failure in concurrent task", e);
        }

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(attempts - 1);
        assertThat(product.history()).hasSize(1);
    }
}

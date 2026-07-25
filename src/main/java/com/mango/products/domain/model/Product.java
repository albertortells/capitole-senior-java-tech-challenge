package com.mango.products.domain.model;

import com.mango.products.domain.exception.InvalidRequestException;
import com.mango.products.domain.exception.PriceOverlapException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Aggregate root. Prices are kept in a {@link ConcurrentSkipListMap} keyed by
 * {@code initDate}, which gives O(log n) lookups/inserts and, being a sorted map,
 * iterates already in chronological order for the full history.
 *
 * <p>Writes ({@link #addPrice}) are serialized per product via {@link #priceLock}
 * because adding a price is a check-then-act operation (verify no overlap, then
 * insert) that must be atomic. Reads ({@link #findPriceAt}, {@link #history}) stay
 * lock-free: {@code ConcurrentSkipListMap} is itself thread-safe and its views are
 * "weakly consistent", which is enough here since there is no requirement for a
 * reader to observe a write from a different client instantaneously.
 */
public final class Product {

    private final Long id;
    private final String name;
    private final String description;
    private final ConcurrentSkipListMap<LocalDate, Price> pricesByInitDate = new ConcurrentSkipListMap<>();

    // Private and final on purpose: never lock on `this`, so nothing outside this
    // class can accidentally synchronize on the same monitor (e.g. a test fixture).
    private final ReentrantLock priceLock = new ReentrantLock();

    private Product(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public static Product create(Long id, String name, String description) {
        if (id == null) {
            throw new InvalidRequestException("id is required");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("name must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidRequestException("description must not be blank");
        }
        return new Product(id, name, description);
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public void addPrice(Price price) {
        DateRange newRange = price.range();
        priceLock.lock();
        try {
            Map.Entry<LocalDate, Price> floor = pricesByInitDate.floorEntry(newRange.initDate());
            if (floor != null && floor.getValue().range().overlaps(newRange)) {
                throw new PriceOverlapException(id, newRange);
            }

            // Any existing price starting within [newInit, newEnd] (both inclusive)
            // necessarily shares at least that starting day with the new range, so it
            // overlaps under our closed-range semantics. `floorEntry` above only ever
            // catches the one candidate that could overlap from the left; this second
            // check catches every candidate that could overlap from the right.
            NavigableMap<LocalDate, Price> rightSide = newRange.isOpenEnded()
                    ? pricesByInitDate.tailMap(newRange.initDate(), true)
                    : pricesByInitDate.subMap(newRange.initDate(), true, newRange.endDate(), true);
            if (!rightSide.isEmpty()) {
                throw new PriceOverlapException(id, newRange);
            }

            pricesByInitDate.put(newRange.initDate(), price);
        } finally {
            priceLock.unlock();
        }
    }

    public Optional<Price> findPriceAt(LocalDate date) {
        Map.Entry<LocalDate, Price> floor = pricesByInitDate.floorEntry(date);
        if (floor == null || !floor.getValue().range().contains(date)) {
            return Optional.empty();
        }
        return Optional.of(floor.getValue());
    }

    /** Chronologically ordered snapshot; unaffected by prices added afterwards. */
    public List<Price> history() {
        return new ArrayList<>(pricesByInitDate.values());
    }
}

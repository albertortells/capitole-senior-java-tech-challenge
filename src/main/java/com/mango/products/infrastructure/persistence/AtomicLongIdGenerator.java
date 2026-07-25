package com.mango.products.infrastructure.persistence;

import com.mango.products.domain.idgen.IdGenerator;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicLongIdGenerator implements IdGenerator {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public Long next() {
        return counter.incrementAndGet();
    }
}

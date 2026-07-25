package com.mango.products.domain.idgen;

/** Strategy: how new entity identifiers are produced (sequential, UUID-based, ...). */
public interface IdGenerator {

    Long next();
}

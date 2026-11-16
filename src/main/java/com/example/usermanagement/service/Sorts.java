package com.example.usermanagement.service;

import com.example.usermanagement.exception.InvalidRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Whitelist of sortable properties. Sorting millions of rows by an unindexed column is a
 * denial-of-service waiting to happen, so only indexed columns are allowed (see V1/V3 migrations).
 */
final class Sorts {

    /**
     * users: PK, unique username, unique email, and the (created_at, id) index.
     */
    static final Set<String> USER = Set.of("id", "username", "email", "createdAt");

    private Sorts() {
    }

    static void require(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidRequestException("Unsupported sort property '%s'. Allowed: %s"
                        .formatted(order.getProperty(), new TreeSet<>(allowed)));
            }
        }
    }
}

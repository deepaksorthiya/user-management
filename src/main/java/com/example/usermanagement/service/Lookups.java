package com.example.usermanagement.service;

import com.example.usermanagement.exception.ResourceNotFoundException;
import com.example.usermanagement.model.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared helper for bulk "assign these ids" operations.
 */
final class Lookups {

    private Lookups() {
    }

    /**
     * Loads every id in one query; throws 404 listing the ids that do not exist.
     */
    static <T extends BaseEntity> Set<T> loadAll(JpaRepository<T, Long> repository,
                                                 Collection<Long> ids,
                                                 String resourceName) {
        Set<Long> requested = new HashSet<>(ids);
        List<T> found = repository.findAllById(requested);
        if (found.size() != requested.size()) {
            Set<Long> foundIds = found.stream().map(BaseEntity::getId).collect(Collectors.toSet());
            List<Long> missing = requested.stream().filter(id -> !foundIds.contains(id)).sorted().toList();
            throw new ResourceNotFoundException("%s not found for ids %s".formatted(resourceName, missing));
        }
        return new HashSet<>(found);
    }
}

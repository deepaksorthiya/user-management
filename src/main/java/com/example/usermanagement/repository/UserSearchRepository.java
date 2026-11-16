package com.example.usermanagement.repository;

import com.example.usermanagement.dto.user.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Custom repository fragment: dynamic user search that reads straight into DTOs
 * (no entities, no persistence-context overhead, no password_hash column fetched).
 */
public interface UserSearchRepository {

    /**
     * Offset pagination. The COUNT query only runs when the result cannot answer "total" by itself.
     */
    Page<UserResponse> search(String q, Boolean active, Pageable pageable);

    /**
     * Keyset (seek) pagination: users with id &gt; afterId ordered by id. Cost is independent of depth.
     */
    List<UserResponse> searchAfter(String q, Boolean active, Long afterId, int limit);
}

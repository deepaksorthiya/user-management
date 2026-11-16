package com.example.usermanagement.dto.common;

import java.util.List;

/**
 * Keyset-pagination result: no total count (that is what makes it cheap on millions of rows).
 * Pass {@code nextCursor} as the {@code after} parameter of the next request.
 */
public record ScrollResponse<T>(
        List<T> content,
        int size,
        boolean hasNext,
        Long nextCursor
) {
}

package io.github.vanilds.creditdesk.shared.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Stable JSON for paginated lists. Serializing Spring's {@code Page} directly leaks internal fields and its
 * shape is not guaranteed between versions, so the contract is defined here.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /** Same choices as the bank back-office grids: 20, 50 or 100 rows per page. */
    public static final Set<Integer> ALLOWED_SIZES = Set.of(20, 50, 100);
    public static final int DEFAULT_SIZE = 20;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static PageRequest request(int page, int size, Sort sort) {
        if (page < 0) {
            throw ApiException.badRequest("validation", "Invalid request", "'page' must be 0 or greater.");
        }
        if (!ALLOWED_SIZES.contains(size)) {
            throw ApiException.badRequest("validation", "Invalid request", "'size' must be 20, 50 or 100.");
        }
        return PageRequest.of(page, size, sort);
    }
}

package com.gomech.api.core.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Standard envelope for every paginated collection endpoint, as defined by
 * ADR-004: REST API Conventions.
 *
 * <p>Spring Data's {@code Page}/{@code PageImpl} is never serialized directly, so the
 * wire format stays owned by the application instead of shifting with a framework upgrade.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                describeSort(page)
        );
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                describeSort(page)
        );
    }

    private static String describeSort(Page<?> page) {
        if (page.getSort().isUnsorted()) {
            return "";
        }
        return page.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .collect(Collectors.joining(";"));
    }
}

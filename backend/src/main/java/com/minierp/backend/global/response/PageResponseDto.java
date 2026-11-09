package com.minierp.backend.global.response;

import java.util.List;

public record PageResponseDto<T>(List<T> content, Page page) {
    public static <T> PageResponseDto<T> of(List<T> content, int page, int size, long total) {
        return new PageResponseDto<>(content, Page.of(page, size, total, content.size()));
    }

    public record Page(
            int number,
            int size,
            long totalElements,
            long totalPages,
            boolean first,
            boolean last,
            int numberOfElements) {
        public static Page of(int page, int size, long total, int numberOfElements) {
            long pages = (total + size - 1) / size;
            return new Page(page, size, total, pages, page == 0, page + 1 >= pages, numberOfElements);
        }
    }
}

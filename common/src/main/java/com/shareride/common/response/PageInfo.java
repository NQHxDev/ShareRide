package com.shareride.common.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Pagination metadata for collection responses. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pagination metadata")
public class PageInfo implements Serializable {

    @Schema(description = "Zero-based page index", example = "0")
    private int page;

    @Schema(description = "Number of elements per page", example = "20")
    private int size;

    @Schema(description = "Total number of elements across all pages", example = "100")
    private long totalElements;

    @Schema(description = "Total number of available pages", example = "5")
    private int totalPages;

    @Schema(description = "Whether there is a subsequent page", example = "true")
    private boolean hasNext;

    @Schema(description = "Whether there is an earlier page", example = "false")
    private boolean hasPrevious;

    @Schema(description = "Whether this is the first page", example = "true")
    private boolean first;

    @Schema(description = "Whether this is the final page", example = "false")
    private boolean last;

    @JsonIgnore
    public boolean isEmpty() {
        return totalElements == 0;
    }

    @JsonIgnore
    public int getOffset() {
        return page * size;
    }

    public static PageInfo empty() {
        return of(0, 0, 0);
    }

    /** Computes pagination metadata assuming 0-based page index. */
    public static PageInfo of(int page, int size, long totalElements) {
        if (page < 0 || size < 0 || totalElements < 0) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        boolean hasNext = totalPages > 0 && page < totalPages - 1;
        boolean hasPrevious = page > 0;
        boolean first = page <= 0;
        boolean last = totalPages == 0 || page >= totalPages - 1;

        return PageInfo.builder()
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(hasNext)
                .hasPrevious(hasPrevious)
                .first(first)
                .last(last)
                .build();
    }

    /** Creates explicit PageInfo with all attributes specified. */
    public static PageInfo of(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious,
            boolean first,
            boolean last) {
        return PageInfo.builder()
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .hasNext(hasNext)
                .hasPrevious(hasPrevious)
                .first(first)
                .last(last)
                .build();
    }
}

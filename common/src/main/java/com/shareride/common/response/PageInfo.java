package com.shareride.common.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Pagination metadata for collection responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageInfo implements Serializable {

   private int page;
   private int size;
   private long totalElements;
   private int totalPages;
   private boolean hasNext;
   private boolean hasPrevious;
   private boolean first;
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

   /**
    * Computes pagination metadata assuming 0-based page index.
    */
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

   /**
    * Creates explicit PageInfo with all attributes specified.
    */
   public static PageInfo of(int page, int size, long totalElements, int totalPages,
         boolean hasNext, boolean hasPrevious, boolean first, boolean last) {
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

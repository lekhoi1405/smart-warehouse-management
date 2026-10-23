
package com.lekhoi.smart_warehouse.domain.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
public class ResultPagination<T> {

    private T result;
    private Meta meta;

    @Getter
    @Setter
    @Builder
    public static class Meta {
        private int current;
        private int pageSize;
        private int pages;
        private long total;
    }
}

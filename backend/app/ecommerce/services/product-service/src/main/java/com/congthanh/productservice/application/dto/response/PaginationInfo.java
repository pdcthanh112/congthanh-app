package com.congthanh.productservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginationInfo {

    private int page;

    private int limit;

    private int totalPage;

    private long totalElement;

}

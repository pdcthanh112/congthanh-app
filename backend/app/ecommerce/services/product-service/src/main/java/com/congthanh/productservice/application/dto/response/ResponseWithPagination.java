package com.congthanh.productservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseWithPagination<T> {

    private PaginationInfo paginationInfo;

    private List<T> responseList;

}

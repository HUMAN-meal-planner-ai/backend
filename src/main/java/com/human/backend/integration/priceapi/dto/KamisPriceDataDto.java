// KAMIS 가격 API 응답의 data 영역을 담는 DTO.
// API 처리 결과 코드(error_code)와 여러 건의 가격 데이터(item)를 포함

package com.human.backend.integration.priceapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KamisPriceDataDto(

        @JsonProperty("error_code")
        String errorCode,

        @JsonProperty("item")
        List<KamisPriceItemDto> items

) {
}

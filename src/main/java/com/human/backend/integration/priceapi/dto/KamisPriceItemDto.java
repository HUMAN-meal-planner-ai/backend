// KAMIS 가격 API에서 반환되는 개별 가격 데이터 1건을 담는 DTO.
// 예: 쌀 / 서울 / 양곡도매 / 2026-09-01 / 2,950원
// KAMIS JSON 필드명을 Java에서 사용하기 편한 필드명으로 매핑

package com.human.backend.integration.priceapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KamisPriceItemDto(

        @JsonProperty("itemname")
        String itemName,

        @JsonProperty("kindname")
        String kindName,

        @JsonProperty("countyname")
        String countyName,

        @JsonProperty("marketname")
        String marketName,

        @JsonProperty("yyyy")
        String year,

        @JsonProperty("regday")
        String regDay,

        @JsonProperty("price")
        String price

) {
}

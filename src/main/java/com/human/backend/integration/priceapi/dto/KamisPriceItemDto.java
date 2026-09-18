package com.human.backend.integration.priceapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
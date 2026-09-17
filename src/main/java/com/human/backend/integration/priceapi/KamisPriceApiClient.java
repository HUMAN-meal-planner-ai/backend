package com.human.backend.integration.priceapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KamisPriceApiClient {

    private final RestClient restClient;

    @Value("${kamis.api.key}")
    private String apiKey;

    @Value("${kamis.api.id}")
    private String apiId;

    public KamisPriceApiClient(
            @Value("${kamis.api.url}") String apiUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public String getPriceData() {

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("action", "periodWholesaleProductList")
                        .queryParam("p_startday", "2026-09-01")
                        .queryParam("p_endday", "2026-09-15")

                        // 테스트용 품목
                        .queryParam("p_itemcategorycode", "100")
                        .queryParam("p_itemcode", "111")
                        .queryParam("p_kindcode", "01")

                        .queryParam("p_productrankcode", "04")
                        .queryParam("p_countrycode", "1101")

                        // kg 단위 환산
                        .queryParam("p_convert_kg_yn", "Y")

                        .queryParam("p_cert_key", apiKey)
                        .queryParam("p_cert_id", apiId)

                        .queryParam("p_returntype", "json")
                        .build())
                .retrieve()
                .body(String.class);
    }
}
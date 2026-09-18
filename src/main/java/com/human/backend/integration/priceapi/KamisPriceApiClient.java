// KAMIS 가격정보 API와 통신하는 외부 API Client.
// KAMIS API가 JSON 데이터를 text/plain 형식으로 반환하므로,
// 응답을 String으로 받은 뒤 ObjectMapper를 이용해 DTO로 변환한다.
// 데이터 저장이나 가격 계산, 단위 변환 등의 비즈니스 로직은 처리하지 않음.

package com.human.backend.integration.priceapi;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.human.backend.integration.priceapi.dto.KamisPriceResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KamisPriceApiClient {

    private final RestClient restClient;
    private final JsonMapper objectMapper;


    @Value("${kamis.api.key}")
    private String apiKey;

    @Value("${kamis.api.id}")
    private String apiId;

    public KamisPriceApiClient(
            @Value("${kamis.api.url}") String apiUrl,
            JsonMapper objectMapper
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();

        this.objectMapper = objectMapper;
    }

    public KamisPriceResponseDto getPriceData() {
        // KAMIS 응답은 JSON 내용이지만 Content-Type이 text/plain이므로
        // 우선 String으로 응답을 받는다.
        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("action", "periodWholesaleProductList")
                        .queryParam("p_startday", "2026-09-01")
                        .queryParam("p_endday", "2026-09-15")

                        .queryParam("p_itemcategorycode", "100")
                        .queryParam("p_itemcode", "111")
                        .queryParam("p_kindcode", "01")
                        .queryParam("p_productrankcode", "04")
                        .queryParam("p_countrycode", "1101")

                        .queryParam("p_convert_kg_yn", "Y")

                        .queryParam("p_cert_key", apiKey)
                        .queryParam("p_cert_id", apiId)

                        .queryParam("p_returntype", "json")
                        .build())
                .retrieve()
                .body(String.class);

        // String 형태의 JSON을 Java DTO로 변환
        try {
            return objectMapper.readValue(
                response, 
                KamisPriceResponseDto.class
            );
        }catch(JacksonException e){
            throw new RuntimeException(
                "KAMIS 가격 API 응답 JSON 변환 실패", e
            );
        }
    }
}
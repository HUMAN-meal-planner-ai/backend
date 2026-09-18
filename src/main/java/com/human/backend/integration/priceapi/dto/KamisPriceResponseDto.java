// KAMIS 가격 API 전체 응답을 받는 최상위 DTO.
// 현재 가격 수집에 필요한 data 영역만 매핑하고, 
// 요청 조건(condition 등)과 같이 사용하지 않는 필드는 무시한다.

package com.human.backend.integration.priceapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KamisPriceResponseDto(KamisPriceDataDto data) {

    
}

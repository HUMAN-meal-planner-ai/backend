// 가격 관련 비즈니스 로직을 처리하는 Service.
// 외부 가격 API를 호출하고, 실제 저장·사용할 가격 데이터만 선별한다.
// 단위 변환, 가격 정제, price_series / ingredient_price 저장 로직도 이 Service에서 처리

package com.human.backend.price.service;

import com.human.backend.integration.priceapi.KamisPriceApiClient;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.integration.priceapi.dto.KamisPriceResponseDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PriceService {
    private final KamisPriceApiClient kamisPriceApiClient;

    public PriceService(KamisPriceApiClient kamisPriceApiClient) {
        this.kamisPriceApiClient = kamisPriceApiClient;
    }

    // KAMIS 가격 데이터를 조회한 뒤 
    // 평균·평년 등의 통계 데이터는 제외하고 실제 시장 가격 데이터만 반환
    public List<KamisPriceItemDto> getActualKamisPrices() {

        KamisPriceResponseDto response =
                kamisPriceApiClient.getPriceData();

        // KAMIS API 자체 오류 확인
        if (response == null
                || response.data() == null
                || !"000".equals(response.data().errorCode())
                || response.data().items() == null) {

            throw new IllegalStateException(
                    "KAMIS 가격 데이터를 정상적으로 조회하지 못했습니다."
            );
        }

        return response.data().items().stream()

                // 평균/평년 데이터는 itemname이 null이므로 제외
                .filter(item -> item.itemName() != null)

                // 실제 시장 데이터인지 한 번 더 확인
                .filter(item -> item.marketName() != null)

                .toList();
    }
}

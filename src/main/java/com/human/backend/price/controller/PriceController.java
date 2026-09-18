// 가격 관련 HTTP 요청을 처리하는 Controller.

package com.human.backend.price.controller;

import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.price.service.PriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    // KAMIS에서 조회한 실제 시장 가격 데이터 확인용 API.
    // 평균·평년 데이터는 제외하고 실제 품목/시장 가격만 반환한다.
    @GetMapping("/kamis-test")
    public List<KamisPriceItemDto> getKamisPrice() {
        return priceService.getActualKamisPrices();
    }
}

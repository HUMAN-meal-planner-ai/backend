package com.human.backend.price.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.human.backend.integration.priceapi.KamisPriceApiClient;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final KamisPriceApiClient kamisPriceApiClient;

    public PriceController(KamisPriceApiClient kamisPriceApiClient) {
        this.kamisPriceApiClient = kamisPriceApiClient;
    }

    @GetMapping("/kamis-test")
    public String getKamisPrice() {
        return kamisPriceApiClient.getPriceData();
    }
}
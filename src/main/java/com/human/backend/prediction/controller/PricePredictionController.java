package com.human.backend.prediction.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.human.backend.prediction.dto.response.PricePredictionCollectionResult;
import com.human.backend.prediction.service.PricePredictionService;

@RestController
@RequestMapping("/api/prices/predictions")
public class PricePredictionController {

    private final PricePredictionService service;

    public PricePredictionController(PricePredictionService service) {
        this.service = service;
    }

    @PostMapping("/collect")
    public PricePredictionCollectionResult collectNextPredictions() {
        return service.generateNextPredictions();
    }

    @PostMapping("/collect/7-days")
    public PricePredictionCollectionResult collectSevenDayPredictions() {
        return service.generateSevenDayPredictions();
    }
}

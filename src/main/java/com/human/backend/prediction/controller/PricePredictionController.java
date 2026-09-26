package com.human.backend.prediction.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.human.backend.prediction.dto.response.PricePredictionCollectionResult;
import com.human.backend.prediction.dto.response.IngredientRiskRankingResponse;
import com.human.backend.prediction.dto.response.PricePredictionChartResponse;
import com.human.backend.prediction.dto.response.WeeklyPricePredictionRiskResponse;
import com.human.backend.prediction.service.PricePredictionQueryService;
import com.human.backend.prediction.service.PricePredictionService;

@RestController
@RequestMapping("/api/prices/predictions")
public class PricePredictionController {

    private final PricePredictionService service;
    private final PricePredictionQueryService queryService;

    public PricePredictionController(
            PricePredictionService service,
            PricePredictionQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @PostMapping("/collect")
    public PricePredictionCollectionResult collectNextPredictions() {
        return service.generateNextPredictions();
    }

    @PostMapping("/collect/7-days")
    public PricePredictionCollectionResult collectSevenDayPredictions() {
        return service.generateSevenDayPredictions();
    }

    @GetMapping("/weekly/{seriesId}")
    public WeeklyPricePredictionRiskResponse getLatestWeeklyPrediction(
            @PathVariable long seriesId) {
        if (seriesId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "시계열 ID는 1 이상이어야 합니다.");
        }
        return queryService.getLatestWeeklyPrediction(seriesId);
    }

    @GetMapping("/weekly/{seriesId}/chart")
    public PricePredictionChartResponse getWeeklyPredictionChart(
            @PathVariable long seriesId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (seriesId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "시계열 ID는 1 이상이어야 합니다.");
        }
        return queryService.getWeeklyPredictionChart(seriesId, startDate, endDate);
    }

    @GetMapping("/weekly/risks")
    public IngredientRiskRankingResponse getWeeklyRiskRankings(
            @RequestParam(defaultValue = "10") int limit) {
        return queryService.getWeeklyRiskRankings(limit);
    }
}

package com.human.backend.price.controller;

import java.time.LocalDate;

import com.human.backend.price.dto.response.PriceCollectionResult;
import com.human.backend.price.dto.response.DailyPriceResponse;
import com.human.backend.price.dto.response.WeeklyPriceResponse;
import com.human.backend.price.service.PriceQueryService;
import com.human.backend.price.service.PriceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PriceService priceService;
    private final PriceQueryService priceQueryService;

    public PriceController(PriceService priceService, PriceQueryService priceQueryService) {
        this.priceService = priceService;
        this.priceQueryService = priceQueryService;
    }

    @GetMapping("/{seriesId}/daily")
    public DailyPriceResponse getDailyPrices(
            @PathVariable Long seriesId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            return priceQueryService.getDailyPrices(seriesId, startDate, endDate);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{seriesId}/weekly")
    public WeeklyPriceResponse getWeeklyPrice(
            @PathVariable Long seriesId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        try {
            return priceQueryService.getWeeklyPrice(seriesId, weekStartDate);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/kamis/collect/{ingredientCode}")
    public PriceCollectionResult collectKamisPrice(
            @PathVariable String ingredientCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            return priceService.collectOne(ingredientCode, startDate, endDate);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}

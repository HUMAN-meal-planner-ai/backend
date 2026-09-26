package com.human.backend.prediction.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.human.backend.prediction.dto.response.WeeklyPricePredictionRiskResponse;
import com.human.backend.prediction.dto.response.ActualPriceChartPoint;
import com.human.backend.prediction.dto.response.IngredientRiskRankItem;
import com.human.backend.prediction.dto.response.IngredientRiskRankingResponse;
import com.human.backend.prediction.dto.response.PricePredictionChartResponse;
import com.human.backend.prediction.dto.response.WeeklyPredictionChartPoint;
import com.human.backend.prediction.service.PricePredictionQueryService;
import com.human.backend.prediction.service.PricePredictionService;

class PricePredictionControllerTest {

    @Test
    void exposesLatestWeeklyPredictionForFrontend() throws Exception {
        PricePredictionService collectionService = mock(PricePredictionService.class);
        PricePredictionQueryService queryService = mock(PricePredictionQueryService.class);
        PricePredictionController controller =
                new PricePredictionController(collectionService, queryService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(queryService.getLatestWeeklyPrediction(3L)).thenReturn(
                new WeeklyPricePredictionRiskResponse(
                        3L,
                        "F00993",
                        "배추",
                        "g",
                        LocalDate.of(2026, 9, 18),
                        LocalDate.of(2026, 9, 25),
                        new BigDecimal("100.000000"),
                        new BigDecimal("110.000000"),
                        new BigDecimal("0.100000"),
                        0.75,
                        0.75,
                        true,
                        "weekly_mean_ridge",
                        "weekly_ridge_v1",
                        Instant.parse("2026-09-23T04:00:00Z")));

        mockMvc.perform(get("/api/prices/predictions/weekly/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesId").value(3))
                .andExpect(jsonPath("$.ingredientCode").value("F00993"))
                .andExpect(jsonPath("$.predictedPrice").value(110.0))
                .andExpect(jsonPath("$.expectedIncreaseRate").value(0.1))
                .andExpect(jsonPath("$.combinedRiskScore").value(0.75))
                .andExpect(jsonPath("$.riskThreshold").value(0.75))
                .andExpect(jsonPath("$.risky").value(true));
    }

    @Test
    void rejectsNonPositiveSeriesIdWithBadRequest() throws Exception {
        PricePredictionService collectionService = mock(PricePredictionService.class);
        PricePredictionQueryService queryService = mock(PricePredictionQueryService.class);
        PricePredictionController controller =
                new PricePredictionController(collectionService, queryService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/prices/predictions/weekly/0"))
                .andExpect(status().isBadRequest());

        verify(queryService, never()).getLatestWeeklyPrediction(0L);
    }

    @Test
    void exposesActualPricesAndSingleWeeklyAveragePredictionForChart() throws Exception {
        PricePredictionService collectionService = mock(PricePredictionService.class);
        PricePredictionQueryService queryService = mock(PricePredictionQueryService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PricePredictionController(collectionService, queryService)).build();
        LocalDate startDate = LocalDate.of(2026, 9, 17);
        LocalDate baseDate = LocalDate.of(2026, 9, 18);
        LocalDate targetDate = LocalDate.of(2026, 9, 25);
        when(queryService.getWeeklyPredictionChart(3L, startDate, targetDate)).thenReturn(
                new PricePredictionChartResponse(
                        3L, "F00993", "배추", "g", startDate, baseDate,
                        List.of(
                                new ActualPriceChartPoint(startDate, new BigDecimal("99.000000")),
                                new ActualPriceChartPoint(baseDate, new BigDecimal("100.000000"))),
                        new WeeklyPredictionChartPoint(
                                baseDate, targetDate,
                                new BigDecimal("100.000000"),
                                new BigDecimal("110.000000"),
                                "NEXT_7_DAY_AVERAGE")));

        mockMvc.perform(get("/api/prices/predictions/weekly/3/chart")
                        .param("startDate", "2026-09-17")
                        .param("endDate", "2026-09-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualPrices.length()").value(2))
                .andExpect(jsonPath("$.weeklyPrediction.targetDate").value("2026-09-25"))
                .andExpect(jsonPath("$.weeklyPrediction.predictedPrice").value(110.0))
                .andExpect(jsonPath("$.weeklyPrediction.predictionMeaning")
                        .value("NEXT_7_DAY_AVERAGE"));
    }

    @Test
    void exposesWeeklyRiskRankingWithDefaultLimit() throws Exception {
        PricePredictionService collectionService = mock(PricePredictionService.class);
        PricePredictionQueryService queryService = mock(PricePredictionQueryService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PricePredictionController(collectionService, queryService)).build();
        when(queryService.getWeeklyRiskRankings(10)).thenReturn(
                new IngredientRiskRankingResponse(
                        10, 1, 2,
                        List.of(new IngredientRiskRankItem(
                                1, 17L, "F00017", "양파", "g",
                                LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 25),
                                new BigDecimal("100.000000"),
                                new BigDecimal("110.000000"),
                                new BigDecimal("115.000000"),
                                new BigDecimal("0.100000"),
                                0.90, 0.70, 0.80, true))));

        mockMvc.perform(get("/api/prices/predictions/weekly/risks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedLimit").value(10))
                .andExpect(jsonPath("$.rankings[0].rank").value(1))
                .andExpect(jsonPath("$.rankings[0].ridgeScore").value(0.9));

        verify(queryService).getWeeklyRiskRankings(10);
    }
}

package com.human.backend.price.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.human.backend.price.dto.response.DailyPricePoint;
import com.human.backend.price.dto.response.DailyPriceResponse;
import com.human.backend.price.dto.response.PriceDataStatus;
import com.human.backend.price.dto.response.WeeklyPriceResponse;
import com.human.backend.price.service.PriceQueryService;
import com.human.backend.price.service.PriceService;

class PriceControllerTest {

    private final PriceService priceService = mock(PriceService.class);
    private final PriceQueryService queryService = mock(PriceQueryService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PriceController(priceService, queryService))
            .build();

    @Test
    void exposesDailyPrices() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 9, 21);
        LocalDate endDate = LocalDate.of(2026, 9, 22);
        when(queryService.getDailyPrices(3L, startDate, endDate)).thenReturn(
                new DailyPriceResponse(
                        3L, "F00993", "g", startDate, endDate, 2,
                        List.of(
                                new DailyPricePoint(startDate, new BigDecimal("100")),
                                new DailyPricePoint(endDate, new BigDecimal("110")))));

        mockMvc.perform(get("/api/prices/3/daily")
                        .param("startDate", "2026-09-21")
                        .param("endDate", "2026-09-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesId").value(3))
                .andExpect(jsonPath("$.observationDays").value(2))
                .andExpect(jsonPath("$.prices[0].price").value(100));
    }

    @Test
    void exposesWeeklyAverageAndComparisonSeparatelyFromDataStatus() throws Exception {
        LocalDate weekStart = LocalDate.of(2026, 9, 21);
        when(queryService.getWeeklyPrice(3L, weekStart)).thenReturn(
                new WeeklyPriceResponse(
                        3L, "F00993", "g", weekStart, weekStart.plusDays(6), 5,
                        new BigDecimal("110.000000"), PriceDataStatus.PARTIAL,
                        new BigDecimal("100.000000"), true, new BigDecimal("10.000000")));

        mockMvc.perform(get("/api/prices/3/weekly")
                        .param("weekStartDate", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStartDate").value("2026-09-21"))
                .andExpect(jsonPath("$.weekEndDate").value("2026-09-27"))
                .andExpect(jsonPath("$.observationDays").value(5))
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.previousWeekComparable").value(true))
                .andExpect(jsonPath("$.weekOverWeekChangeRatePercent").value(10.0));
    }

    @Test
    void returnsBadRequestForInvalidSeriesId() throws Exception {
        when(queryService.getDailyPrices(
                0L, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22)))
                .thenThrow(new IllegalArgumentException("seriesId must be positive"));

        mockMvc.perform(get("/api/prices/0/daily")
                        .param("startDate", "2026-09-21")
                        .param("endDate", "2026-09-22"))
                .andExpect(status().isBadRequest());

        verify(queryService).getDailyPrices(
                0L, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22));
        verify(queryService, never()).getWeeklyPrice(0L, LocalDate.of(2026, 9, 21));
    }
}

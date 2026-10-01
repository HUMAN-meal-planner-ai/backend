package com.human.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class AuthFacilityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void signupLoginAndCreateFacility() throws Exception {
                String email = "owner-" + UUID.randomUUID() + "@mealfit.test";

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                    {
                                            "email": "%s",
                      "password": "password123!",
                      "name": "테스트 사용자"
                    }
                                        """.formatted(email.toUpperCase(java.util.Locale.ROOT))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.facilityId").doesNotExist());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                                            "email": "%s",
                      "password": "password123!"
                    }
                                        """.formatted(email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(patch("/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"수정된 사용자\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("수정된 사용자"));

        MvcResult facilityResult = mockMvc.perform(post("/api/facilities")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "MealFit 테스트 시설",
                      "facilityType": "SCHOOL",
                      "address": "서울시",
                      "contactName": "영양사",
                      "defaultMealCount": 100,
                      "breakfastMealCount": 0,
                      "lunchMealCount": 100,
                      "dinnerMealCount": 0,
                                            "targetFoodCost": 4500,
                                            "monthlyBudget": 1200000
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("MealFit 테스트 시설"))
            .andExpect(jsonPath("$.defaultMealCount").value(100))
                        .andExpect(jsonPath("$.monthlyBudget").value(1200000))
            .andReturn();

        JsonNode facilityJson = objectMapper.readTree(facilityResult.getResponse().getContentAsString());
        long facilityId = facilityJson.get("facilityId").asLong();
        long userId = loginJson.path("user").path("userId").asLong();
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Seoul"));
        jdbcTemplate.update(
            "INSERT INTO mealfit.meal_plan (facility_id, user_id, plan_date, meal_type, meal_count, version) VALUES (?, ?, ?, ?, ?, 1)",
            facilityId, userId, month.atDay(2), "LUNCH", 100);
        jdbcTemplate.update(
            "INSERT INTO mealfit.meal_plan (facility_id, user_id, plan_date, meal_type, meal_count, version) VALUES (?, ?, ?, ?, ?, 1)",
            facilityId, userId, month.atDay(3), "DINNER", 20);

        mockMvc.perform(post("/api/facilities/me/monthly-budget")
                .header("Authorization", "Bearer " + token)
                .param("month", month.atDay(1).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.monthlyBudget").value(540000))
            .andExpect(jsonPath("$.monthlyBudgetMonth").value(month.toString()));

        mockMvc.perform(get("/api/facilities/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetFoodCost").value(4500))
            .andExpect(jsonPath("$.monthlyBudget").value(540000));

        mockMvc.perform(get("/api/meal-plans/weekly")
                .header("Authorization", "Bearer " + token)
                .param("weekStartDate", month.plusMonths(1).atDay(1).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meals.length()").value(0));

        mockMvc.perform(get("/api/facilities/me/monthly-budgets")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[0].month").value(month.toString()))
            .andExpect(jsonPath("$[5].month").value(month.plusMonths(5).toString()))
            .andExpect(jsonPath("$[0].budgetAmount").value(540000));

        mockMvc.perform(put("/api/facilities/me/monthly-budget/executed")
                .header("Authorization", "Bearer " + token)
                .param("month", month.atDay(1).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"executedAmount\":120000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgetAmount").value(540000))
            .andExpect(jsonPath("$.executedAmount").value(120000));

        mockMvc.perform(get("/api/facilities/me/monthly-budgets")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].executedAmount").value(120000));

        YearMonth nextMonth = month.plusMonths(1);
        mockMvc.perform(put("/api/facilities/me/monthly-budget")
                .header("Authorization", "Bearer " + token)
                .param("month", nextMonth.atDay(1).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"budgetAmount\":123456.78}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value(nextMonth.toString()))
            .andExpect(jsonPath("$.budgetAmount").value(123456.78));

        mockMvc.perform(get("/api/facilities/me/monthly-budgets")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[1].budgetAmount").value(123456.78));

        mockMvc.perform(put("/api/facilities/me/monthly-budget")
                .header("Authorization", "Bearer " + token)
                .param("month", month.plusMonths(6).atDay(1).toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"budgetAmount\":100000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MONTHLY_BUDGET_MONTH_OUT_OF_RANGE"));

            mockMvc.perform(patch("/api/facilities/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "수정된 MealFit 시설",
                      "facilityType": "COMPANY",
                      "address": "부산시",
                      "contactName": "새 담당자",
                      "defaultMealCount": 120,
                      "breakfastMealCount": 10,
                      "lunchMealCount": 80,
                      "dinnerMealCount": 30,
                      "targetFoodCost": 5000
                    }
                        """))
                    .andExpect(status().isForbidden());
    }

    @Test
    void protectedApiRejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validationRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"short@mealfit.test","password":"123","name":"사용자"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}

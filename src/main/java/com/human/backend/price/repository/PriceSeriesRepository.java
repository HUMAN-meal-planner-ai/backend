package com.human.backend.price.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.human.backend.price.entity.PriceSeries;

public interface PriceSeriesRepository extends JpaRepository<PriceSeries, Long> {

    interface PricePredictionTarget {
        Long getSeriesId();
        LocalDate getBaseDate();
        BigDecimal getBasePrice();
        String getStandardUnit();
    }

    @Query("""
            SELECT ps
            FROM PriceSeries ps
            JOIN FETCH ps.ingredient ingredient
            WHERE ps.sourceName = 'KAMIS'
              AND ingredient.active = true
              AND ps.sourceCategoryCode IS NOT NULL
              AND ps.sourceItemCode IS NOT NULL
              AND ps.sourceKindCode IS NOT NULL
              AND ps.sourceRankCode IS NOT NULL
            ORDER BY ps.id
            """)
    List<PriceSeries> findAllActiveKamisCollectionTargets();

    @Query("""
            SELECT ps
            FROM PriceSeries ps
            JOIN FETCH ps.ingredient ingredient
            WHERE ps.sourceName = 'KAMIS'
              AND ingredient.active = true
              AND UPPER(ingredient.ingredientCode) = UPPER(:ingredientCode)
              AND ps.sourceCategoryCode IS NOT NULL
              AND ps.sourceItemCode IS NOT NULL
              AND ps.sourceKindCode IS NOT NULL
              AND ps.sourceRankCode IS NOT NULL
            ORDER BY ps.id
            """)
    List<PriceSeries> findAllActiveKamisCollectionTargetsByIngredientCode(
            @Param("ingredientCode") String ingredientCode);

    @Query(value = """
            SELECT DISTINCT ON (ps.series_id)
                ps.series_id AS "seriesId",
                ip.price_date AS "baseDate",
                ip.standard_unit_price AS "basePrice",
                ingredient.standard_unit AS "standardUnit"
            FROM mealfit.price_series ps
            JOIN mealfit.ingredient ingredient
              ON ingredient.ingredient_id = ps.ingredient_id
            JOIN mealfit.ingredient_price ip
              ON ip.series_id = ps.series_id
            WHERE ingredient.is_active = true
            ORDER BY ps.series_id, ip.price_date DESC, ip.price_id DESC
            """, nativeQuery = true)
    List<PricePredictionTarget> findAllActivePredictionTargets();
}

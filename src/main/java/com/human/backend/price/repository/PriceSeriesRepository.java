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

    interface WeeklyRepresentativePrice {
        Long getSeriesId();
        String getIngredientCode();
        LocalDate getPriceDate();
        BigDecimal getRepresentativePrice();
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

    @Query(value = """
            WITH canonical_series AS (
                SELECT
                    ps.series_id,
                    ps.ingredient_id,
                    ps.source_name,
                    ps.source_category_code,
                    ps.source_item_code,
                    ps.source_kind_code,
                    ps.source_rank_code,
                    ps.variety,
                    ps.grade,
                    ps.price_type,
                    ps.market,
                    ps.original_unit,
                    ps.unit_quantity,
                    ingredient.ingredient_code,
                    ingredient.standard_unit
                FROM mealfit.price_series ps
                JOIN mealfit.ingredient ingredient
                  ON ingredient.ingredient_id = ps.ingredient_id
                WHERE ps.series_id IN (:seriesIds)
                  AND ps.source_name = 'KAMIS'
                  AND (UPPER(ps.region) = 'SEOUL' OR ps.region = '서울')
                  AND ingredient.is_active = true
            ),
            representative_prices AS (
                SELECT
                    canonical.series_id,
                    canonical.ingredient_code,
                    canonical.standard_unit,
                    ip.price_date,
                    AVG(ip.standard_unit_price) AS representative_price
                FROM canonical_series canonical
                JOIN mealfit.price_series regional
                  ON regional.ingredient_id = canonical.ingredient_id
                 AND regional.source_name = canonical.source_name
                 AND regional.source_category_code = canonical.source_category_code
                 AND regional.source_item_code = canonical.source_item_code
                 AND regional.source_kind_code = canonical.source_kind_code
                 AND regional.source_rank_code = canonical.source_rank_code
                 AND regional.variety = canonical.variety
                 AND regional.grade = canonical.grade
                 AND regional.price_type = canonical.price_type
                 AND regional.market = canonical.market
                 AND regional.original_unit = canonical.original_unit
                 AND regional.unit_quantity = canonical.unit_quantity
                 AND (UPPER(regional.region) IN ('SEOUL', 'BUSAN', 'DAEJEON')
                      OR regional.region IN ('서울', '부산', '대전'))
                JOIN mealfit.ingredient_price ip
                  ON ip.series_id = regional.series_id
                GROUP BY
                    canonical.series_id,
                    canonical.ingredient_code,
                    canonical.standard_unit,
                    ip.price_date
                HAVING COUNT(DISTINCT CASE
                    WHEN UPPER(regional.region) = 'SEOUL' OR regional.region = '서울'
                        THEN 'SEOUL'
                    WHEN UPPER(regional.region) = 'BUSAN' OR regional.region = '부산'
                        THEN 'BUSAN'
                    WHEN UPPER(regional.region) = 'DAEJEON' OR regional.region = '대전'
                        THEN 'DAEJEON'
                END) = 3
            ),
            ranked_prices AS (
                SELECT
                    representative.*,
                    ROW_NUMBER() OVER (
                        PARTITION BY representative.series_id
                        ORDER BY representative.price_date DESC
                    ) AS recent_order
                FROM representative_prices representative
            )
            SELECT
                series_id AS "seriesId",
                ingredient_code AS "ingredientCode",
                price_date AS "priceDate",
                representative_price AS "representativePrice",
                standard_unit AS "standardUnit"
            FROM ranked_prices
            WHERE recent_order <= 400
            ORDER BY series_id, price_date
            """, nativeQuery = true)
    List<WeeklyRepresentativePrice> findWeeklyRepresentativePrices(
            @Param("seriesIds") List<Long> seriesIds);
}

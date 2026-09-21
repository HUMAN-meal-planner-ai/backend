package com.human.backend.price.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.human.backend.price.entity.PriceSeries;

public interface PriceSeriesRepository extends JpaRepository<PriceSeries, Long> {

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
}

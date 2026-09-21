package com.human.backend.price.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.human.backend.price.entity.PriceSeries;

public interface PriceSeriesRepository extends JpaRepository<PriceSeries, Long> {

    @Query("""
            SELECT ps
            FROM PriceSeries ps
            WHERE ps.ingredient.id = :ingredientId
              AND ps.sourceName = :sourceName
              AND ps.sourceItemCode = :sourceItemCode
              AND ps.variety = :variety
              AND ps.grade = :grade
              AND ps.priceType = :priceType
              AND ps.market = :market
              AND ps.region = :region
              AND ps.originalUnit = :originalUnit
              AND ps.unitQuantity = :unitQuantity
            """)
    Optional<PriceSeries> findByNaturalKey(
            @Param("ingredientId") Long ingredientId,
            @Param("sourceName") String sourceName,
            @Param("sourceItemCode") String sourceItemCode,
            @Param("variety") String variety,
            @Param("grade") String grade,
            @Param("priceType") String priceType,
            @Param("market") String market,
            @Param("region") String region,
            @Param("originalUnit") String originalUnit,
            @Param("unitQuantity") BigDecimal unitQuantity);
}

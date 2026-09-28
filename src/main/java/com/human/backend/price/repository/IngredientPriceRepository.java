package com.human.backend.price.repository;

import java.time.LocalDate;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.human.backend.price.entity.IngredientPrice;

public interface IngredientPriceRepository extends JpaRepository<IngredientPrice, Long> {
    boolean existsBySeries_IdAndPriceDate(Long seriesId, LocalDate priceDate);

    /** 관리자 화면에서 전체 가격 데이터의 가장 최근 시장 가격 기준일을 확인합니다. */
    @Query("SELECT MAX(price.priceDate) FROM IngredientPrice price")
    LocalDate findLatestPriceDate();

    /** 가장 최근 기준일에 실제로 저장된 가격 행 수를 반환합니다. */
    long countByPriceDate(LocalDate priceDate);

    /**
     * 수집 시각이 기록된 행 가운데 가장 최근 DB 저장 시각을 확인합니다.
     * 가격 기준일과 별도로 제공해 과거 데이터가 최근에 재수집된 경우도 구분할 수 있습니다.
     */
    @Query("SELECT MAX(price.collectedAt) FROM IngredientPrice price")
    Instant findLatestCollectedAt();
}

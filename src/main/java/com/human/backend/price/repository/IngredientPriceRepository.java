package com.human.backend.price.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.backend.price.entity.IngredientPrice;

public interface IngredientPriceRepository extends JpaRepository<IngredientPrice, Long> {
    boolean existsBySeries_IdAndPriceDate(Long seriesId, LocalDate priceDate);
}

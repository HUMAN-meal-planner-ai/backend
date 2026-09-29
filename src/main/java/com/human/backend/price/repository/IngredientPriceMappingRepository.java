package com.human.backend.price.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.human.backend.price.entity.IngredientPriceMapping;

public interface IngredientPriceMappingRepository
        extends JpaRepository<IngredientPriceMapping, Long> {

    @EntityGraph(attributePaths = {"ingredient", "series", "series.ingredient"})
    List<IngredientPriceMapping> findAllByOrderByReviewStatusAscPriorityAscIdAsc();
}

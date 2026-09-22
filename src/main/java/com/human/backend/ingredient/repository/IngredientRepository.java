package com.human.backend.ingredient.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.backend.ingredient.entity.Ingredient;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    Optional<Ingredient> findByIngredientCode(String ingredientCode);
}

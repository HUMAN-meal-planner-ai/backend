package com.human.backend.prediction.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.prediction.dto.response.AiPricePredictionResponse;
import com.human.backend.prediction.repository.PricePredictionRepository;

@Service
public class PricePredictionStorageService {

    private final PricePredictionRepository repository;

    public PricePredictionStorageService(PricePredictionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StoreResult store(List<AiPricePredictionResponse> predictions) {
        int inserted = 0;
        for (AiPricePredictionResponse prediction : predictions) {
            inserted += repository.insertIfAbsent(
                    prediction.seriesId(), prediction.baseDate(), prediction.targetDate(),
                    prediction.basePrice(), prediction.predictedPrice(), prediction.modelName(),
                    prediction.modelVersion(), prediction.generatedAt());
        }
        return new StoreResult(inserted, predictions.size() - inserted);
    }

    public record StoreResult(int inserted, int duplicatesSkipped) {
    }
}

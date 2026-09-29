package com.human.backend.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.admin.dto.response.AdminPriceMappingResponse;
import com.human.backend.price.entity.IngredientPriceMapping;
import com.human.backend.price.repository.IngredientPriceMappingRepository;

@Service
public class AdminPriceMappingService {
    private final IngredientPriceMappingRepository repository;

    public AdminPriceMappingService(IngredientPriceMappingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AdminPriceMappingResponse> getMappings() {
        return repository.findAllByOrderByReviewStatusAscPriorityAscIdAsc().stream()
                .map(AdminPriceMappingResponse::from)
                .toList();
    }

    @Transactional
    public AdminPriceMappingResponse review(Long mappingId, String status) {
        IngredientPriceMapping mapping = repository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("가격 매핑을 찾을 수 없습니다: " + mappingId));
        mapping.review(status);
        return AdminPriceMappingResponse.from(mapping);
    }
}


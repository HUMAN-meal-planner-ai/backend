package com.human.backend.cost.service;

import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.entity.MenuIngredientCostVo;
import com.human.backend.cost.repository.CostRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class CostService {

    private final CostRepository costRepository;

    // 서비스 객체에 필드변수로 레포지토리 저장
    public CostService(CostRepository costRepository) {
        this.costRepository = costRepository;
    }

    /**
     * 최신 식재료 가격과 사용 중량으로 메뉴 현재 원가를 계산한다.
     */

    // 아까 생성한 response DTO를 반환하는 계산 메서드 생성
    public MenuCostResponse calculateCurrentMenuCost(Long menuId) {

        // 메뉴를 가져오는데 ID에 해당하는 거 없으면 오류 반환
        String menuName = costRepository.findMenuNameById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴 ID입니다. ID=" + menuId));

        // 메뉴에 해당하는 식재료 정보 리스트를 가져오는데 없으면 오류 반환
        List<MenuIngredientCostVo> items = costRepository.findLatestIngredientsByMenuId(menuId);
        if (items.isEmpty()) {
            throw new IllegalStateException("해당 메뉴에 등록된 식재료 구성 정보가 없습니다. ID=" + menuId);
        }

        // [테스트 1] 어떤 메뉴를 계산하기 시작했는지 출력
        System.out.println("==================================================");
        System.out.println(">> [원가 계산 시작] 메뉴 ID: " + menuId + " / 메뉴명: " + menuName);
        System.out.println(">> 식재료 품목 수: " + items.size() + "개");
        System.out.println("--------------------------------------------------");

        BigDecimal totalCost = BigDecimal.ZERO; // 메뉴 원가를 담을 변수
        List<MenuCostResponse.IngredientDetail> detailList = new ArrayList<>(); // 식재료 정보 저장하는 리스트

        // 미리 가져온 식재료 정보리스트 순회
        for (MenuIngredientCostVo item : items) {
            BigDecimal quantity = item.getQuantity();
            BigDecimal unitPrice = item.getStandardUnitPrice();

            // 원가 = 사용량(g) * 최신 기준단위 가격(원/g)
            BigDecimal lineCost = quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
            totalCost = totalCost.add(lineCost);

            // [테스트 2] 개별 식재료별 중량, 단가, 곱셈 결과 및 누적 합계 출력
            System.out.println(String.format(
                    "   - [%s] 사용량: %sg | 단가: %s원/g | 재료원가: %s원 (누적: %s원)",
                    item.getIngredientName(), quantity, unitPrice, lineCost, totalCost));

            detailList.add(new MenuCostResponse.IngredientDetail(
                    item.getIngredientId(),
                    item.getIngredientName(),
                    quantity,
                    unitPrice,
                    lineCost,
                    item.getPriceDate()));
        }

        // [테스트 3] 최종 반환 전 1인분 총 원가 결과 출력
        System.out.println("--------------------------------------------------");
        System.out.println(">> [최종 계산 완료] 1인분 총 원가: " + totalCost + "원");
        System.out.println("==================================================");

        return new MenuCostResponse(menuId, menuName, totalCost, detailList); // 메뉴ID, 메뉴이름, 메뉴 원가, 메뉴 식재료 정보를 담아서 리스폰스 반환
    }
}

// 외부 가격 데이터의 단위를 식재료의 기준 단위(g, ml, ea)로 환산하는 클래스.
// 가격 자체를 변환하는 것이 아니라, 해당 가격이 기준 단위 몇 개에 해당하는지를 계산한다.

package com.human.backend.price.util;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component 
public class PriceUnitConverter {
    
    // 외부 API 단위를 식재료 기준 단위 수량으로 변환한다.
    // 예: 1kg -> 1000g / 1L -> 1000ml / 30개 -> 30ea

    public BigDecimal convertQuantity(
            BigDecimal quantity,
            String sourceUnit,
            String standardUnit
    ) {

        if (quantity == null || sourceUnit == null || standardUnit == null) {
            throw new IllegalArgumentException("단위 변환에 필요한 값이 없습니다.");
        }

        String source = normalize(sourceUnit);
        String target = normalize(standardUnit);

        // 무게
        if (target.equals("g")) {

            return switch (source) {
                case "g" -> quantity;
                case "kg" -> quantity.multiply(BigDecimal.valueOf(1000));
                default -> throw unsupported(sourceUnit, standardUnit);
            };
        }

        // 부피
        if (target.equals("ml")) {

            return switch (source) {
                case "ml" -> quantity;
                case "l" -> quantity.multiply(BigDecimal.valueOf(1000));
                default -> throw unsupported(sourceUnit, standardUnit);
            };
        }

        // 개수
        if (target.equals("ea")) {

            return switch (source) {
                case "ea", "개" -> quantity;
                default -> throw unsupported(sourceUnit, standardUnit);
            };
        }

        throw new IllegalArgumentException(
                "지원하지 않는 기준 단위입니다: " + standardUnit
        );
    }

    /**
     * 단위 표기를 비교하기 쉽도록 통일한다.
     */
    private String normalize(String unit) {
        return unit.trim().toLowerCase();
    }

    /**
     * 서로 변환할 수 없는 단위가 들어온 경우 예외를 생성한다.
     */
    private IllegalArgumentException unsupported(
            String sourceUnit,
            String standardUnit
    ) {
        return new IllegalArgumentException(
                "변환할 수 없는 단위입니다: "
                        + sourceUnit
                        + " -> "
                        + standardUnit
        );
    }
}

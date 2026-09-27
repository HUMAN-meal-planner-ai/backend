package com.human.backend.integration.priceapi;

import java.util.Arrays;

/**
 * 가격 시계열의 지역 식별값과 KAMIS 지역 코드를 한곳에서 관리한다.
 */
public enum KamisRegion {
    SEOUL("서울", "1101"),
    BUSAN("부산", "2100"),
    DAEJEON("대전", "2501");

    private final String displayName;
    private final String countryCode;

    KamisRegion(String displayName, String countryCode) {
        this.displayName = displayName;
        this.countryCode = countryCode;
    }

    public String displayName() {
        return displayName;
    }

    public String countryCode() {
        return countryCode;
    }

    public static KamisRegion fromSeriesRegion(String region) {
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("KAMIS 가격 시계열의 지역이 비어 있습니다.");
        }

        String normalized = region.trim();
        return Arrays.stream(values())
                .filter(candidate -> candidate.displayName.equals(normalized)
                        || candidate.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 KAMIS 수집 지역입니다: " + region));
    }
}

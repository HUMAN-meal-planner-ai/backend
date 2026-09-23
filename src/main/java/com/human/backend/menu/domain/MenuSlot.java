package com.human.backend.menu.domain;

public enum MenuSlot {
    RICE,
    SOUP,
    MAIN,
    SIDE,
    KIMCHI,
    OTHER;

    public static MenuSlot from(String mainCategory, String subCategory) {
        String category = ((mainCategory == null ? "" : mainCategory) + " "
                + (subCategory == null ? "" : subCategory)).toLowerCase();

        if (category.contains("밥") || category.contains("rice")) return RICE;
        if (category.contains("국") || category.contains("찌개") || category.contains("soup")) return SOUP;
        if (category.contains("김치") || category.contains("kimchi")) return KIMCHI;
        if (category.contains("주찬") || category.contains("main")) return MAIN;
        if (category.contains("부찬") || category.contains("side")) return SIDE;
        return OTHER;
    }
}

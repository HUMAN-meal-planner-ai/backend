package com.human.backend.menu.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter 
@AllArgsConstructor 
public class MenuResponse {
    private String menuCode;
    private String menuName;
    private String mainCategory;
    private String subCategory;
    private Double weight;
    private Integer foodCount;
} 

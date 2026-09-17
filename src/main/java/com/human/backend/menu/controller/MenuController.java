package com.human.backend.menu.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController 
public class MenuController{
    @GetMapping("/api/menus")
    public String getMethodName(@RequestParam String param) {
        return new String();
    }
    
}

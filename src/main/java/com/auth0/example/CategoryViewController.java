package com.auth0.example;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CategoryViewController {

    @GetMapping("/categories")
    public String categories() {
        return "categories";
    }
} 
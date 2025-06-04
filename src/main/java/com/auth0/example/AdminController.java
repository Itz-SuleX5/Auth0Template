package com.auth0.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.Map;

@Controller
public class AdminController {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @GetMapping("/admin")
    public String admin(Model model, @AuthenticationPrincipal OidcUser principal) {
        logger.info("Accediendo a /admin");
        
        if (principal != null) {
            Map<String, Object> claims = principal.getClaims();
            logger.info("Claims del usuario: {}", claims);
            
            // Verificar roles
            List<String> roles = (List<String>) claims.get("https://my-app.example.com/roles");
            logger.info("Roles del usuario: {}", roles);
            
            if (roles != null && roles.contains("ADMIN")) {
                model.addAttribute("profile", claims);
                return "admin";
            }
        }
        
        logger.warn("Acceso denegado a /admin");
        return "error/403";
    }
} 
package com.auth0.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        logger.error("Error en la aplicación: ", e);
        
        String errorMessage = "Ha ocurrido un error: " + e.getMessage();
        redirectAttributes.addFlashAttribute("error", errorMessage);
        
        // Si es una petición AJAX, devolver un error JSON
        if (request.getHeader("X-Requested-With") != null) {
            ModelAndView mav = new ModelAndView("error");
            mav.addObject("error", errorMessage);
            return mav;
        }
        
        // Para peticiones normales, redirigir a la página anterior
        String referer = request.getHeader("Referer");
        if (referer != null) {
            return new ModelAndView("redirect:" + referer);
        }
        
        // Si no hay página anterior, ir al inicio
        return new ModelAndView("redirect:/");
    }
} 
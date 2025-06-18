package com.auth0.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
@Transactional
public class CategoryController {

    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public CategoryController() {
        logger.info("Inicializando CategoryController");
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        logger.info("GET /api/categories/test - Endpoint de prueba");
        return ResponseEntity.ok("El controlador de categorías está funcionando - " + System.currentTimeMillis());
    }

    @GetMapping
    public ResponseEntity<?> getAllCategories(@AuthenticationPrincipal OidcUser principal) {
        try {
            logger.info("GET /api/categories - Obteniendo todas las categorías para el usuario");
            String userEmail = principal != null ? principal.getEmail() : null;
            List<Category> categories;
            if (userEmail != null) {
                // Mostrar categorías globales (userMail null) y del usuario
                categories = categoryRepository.findAll().stream()
                    .filter(category -> category.getUserMail() == null || userEmail.equals(category.getUserMail()))
                    .filter(category -> !"No especificado".equals(category.getName()))
                    .collect(Collectors.toList());
            } else {
                categories = categoryRepository.findAll().stream()
                    .filter(category -> !"No especificado".equals(category.getName()))
                    .collect(Collectors.toList());
            }
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            logger.error("Error al obtener las categorías", e);
            return ResponseEntity.internalServerError().body("Error al obtener las categorías: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createCategory(@RequestBody Category category, @AuthenticationPrincipal OidcUser principal) {
        try {
            logger.info("POST /api/categories - Recibida petición para crear categoría: {}", category);
            if (category == null) {
                logger.error("Error: La categoría no puede ser nula");
                return ResponseEntity.badRequest().body("La categoría no puede ser nula");
            }
            if (category.getName() == null || category.getName().trim().isEmpty()) {
                logger.error("Error: El nombre es requerido");
                return ResponseEntity.badRequest().body("El nombre es requerido");
            }
            if (category.getIcon() == null || category.getIcon().trim().isEmpty()) {
                logger.error("Error: El icono es requerido");
                return ResponseEntity.badRequest().body("El icono es requerido");
            }
            // Asignar SIEMPRE el correo del usuario autenticado
            if (principal != null) {
                category.setUserMail(principal.getEmail());
            } else {
                category.setUserMail(null);
            }
            // Manejar la categoría padre
            if (category.getParentCategoryId() != null) {
                Category parentCategory = categoryRepository.findById(category.getParentCategoryId())
                    .orElseThrow(() -> new RuntimeException("Categoría padre no encontrada"));
                category.setParentCategory(parentCategory);
            }
            Category savedCategory = categoryRepository.save(category);
            logger.info("Categoría creada exitosamente: {}", savedCategory);
            return ResponseEntity.ok(savedCategory);
        } catch (Exception e) {
            logger.error("Error al crear la categoría", e);
            return ResponseEntity.badRequest().body("Error al crear la categoría: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable Long id) {
        try {
            logger.info("GET /api/categories/{} - Obteniendo categoría por ID", id);
            return categoryRepository.findById(id)
                    .map(category -> {
                        try {
                            String categoryJson = objectMapper.writeValueAsString(category);
                            logger.debug("Categoría encontrada: {}", categoryJson);
                            return ResponseEntity.ok(category);
                        } catch (Exception e) {
                            logger.error("Error al serializar categoría", e);
                            return ResponseEntity.ok(category);
                        }
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error al obtener la categoría", e);
            return ResponseEntity.internalServerError().body("Error al obtener la categoría: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody Category categoryDetails) {
        try {
            logger.info("PUT /api/categories/{} - Actualizando categoría", id);
            return categoryRepository.findById(id)
                    .map(existingCategory -> {
                        existingCategory.setName(categoryDetails.getName());
                        existingCategory.setDescription(categoryDetails.getDescription());
                        existingCategory.setIcon(categoryDetails.getIcon());
                        
                        // Manejar la categoría padre
                        if (categoryDetails.getParentCategoryId() != null) {
                            Category parentCategory = categoryRepository.findById(categoryDetails.getParentCategoryId())
                                .orElseThrow(() -> new RuntimeException("Categoría padre no encontrada"));
                            existingCategory.setParentCategory(parentCategory);
                        } else {
                            existingCategory.setParentCategoryId(null);
                        }
                        
                        Category updatedCategory = categoryRepository.save(existingCategory);
                        logger.info("Categoría actualizada exitosamente: {}", updatedCategory);
                        return ResponseEntity.ok(updatedCategory);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error al actualizar la categoría", e);
            return ResponseEntity.internalServerError().body("Error al actualizar la categoría: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        try {
            logger.info("DELETE /api/categories/{} - Eliminando categoría", id);
            return categoryRepository.findById(id)
                    .map(category -> {
                        // Buscar la categoría "No especificado"
                        Category noEspecificado = categoryRepository.findByName("No especificado");
                        if (noEspecificado == null) {
                            // Si no existe, crearla
                            noEspecificado = new Category();
                            noEspecificado.setName("No especificado");
                            noEspecificado.setIcon("fas fa-question-circle");
                            noEspecificado = categoryRepository.save(noEspecificado);
                        }

                        // Actualizar todas las transacciones que usan esta categoría
                        List<Transaction> transactions = transactionRepository.findByCategoryId(id);
                        for (Transaction transaction : transactions) {
                            transaction.setCategory(noEspecificado);
                        }
                        transactionRepository.saveAll(transactions);

                        // Ahora podemos eliminar la categoría
                        categoryRepository.delete(category);
                        logger.info("Categoría eliminada exitosamente");
                        return ResponseEntity.ok().build();
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            logger.error("Error al eliminar la categoría", e);
            return ResponseEntity.internalServerError().body("Error al eliminar la categoría: " + e.getMessage());
        }
    }
}
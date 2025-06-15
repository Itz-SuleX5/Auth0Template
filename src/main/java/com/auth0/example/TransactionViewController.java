package com.auth0.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.math.BigDecimal;
import java.util.List;

@Controller
public class TransactionViewController {
    private static final Logger logger = LoggerFactory.getLogger(TransactionViewController.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping("/new-transaction")
    public String showNewTransactionForm(Model model, @AuthenticationPrincipal OidcUser principal) {
        try {
            logger.info("=== INICIO showNewTransactionForm ===");
            logger.info("Creando nueva transacción");
            Transaction transaction = new Transaction();
            model.addAttribute("transaction", transaction);
            
            logger.info("Obteniendo categorías");
            List<Category> categories = categoryRepository.findAll();
            logger.info("Categorías encontradas: {}", categories.size());
            model.addAttribute("categories", categories);
            
            // Agregar el perfil del usuario al modelo
            model.addAttribute("profile", principal.getClaims());
            
            logger.info("=== FIN showNewTransactionForm ===");
            return "new-transaction";
        } catch (Exception e) {
            logger.error("ERROR CRÍTICO en showNewTransactionForm", e);
            throw new RuntimeException("Error al mostrar el formulario de nueva transacción", e);
        }
    }

    @PostMapping("/new-transaction")
    public String createTransaction(@ModelAttribute Transaction transaction, 
                                  @AuthenticationPrincipal OidcUser principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            logger.info("=== INICIO createTransaction ===");
            logger.info("Transacción recibida: {}", transaction);
            
            // Obtener el correo del usuario actual
            String userEmail = (String) principal.getClaims().get("email");
            transaction.setUserMail(userEmail);
            logger.info("Correo del usuario asignado: {}", userEmail);
            
            // Validar el monto
            if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                logger.error("Monto inválido: {}", transaction.getAmount());
                redirectAttributes.addFlashAttribute("error", "El monto debe ser mayor que 0");
                return "redirect:/new-transaction";
            }

            // Validar la categoría
            if (transaction.getCategory() == null || transaction.getCategory().getId() == null) {
                logger.error("Categoría inválida: {}", transaction.getCategory());
                redirectAttributes.addFlashAttribute("error", "La categoría es requerida");
                return "redirect:/new-transaction";
            }

            // Validar el tipo de transacción
            if (transaction.getType() == null) {
                logger.error("Tipo de transacción inválido");
                redirectAttributes.addFlashAttribute("error", "El tipo de transacción es requerido");
                return "redirect:/new-transaction";
            }

            logger.info("Guardando transacción");
            transactionRepository.save(transaction);
            logger.info("Transacción guardada exitosamente");
            
            logger.info("=== FIN createTransaction ===");
            redirectAttributes.addFlashAttribute("success", "Transacción creada exitosamente");
            return "redirect:/";
        } catch (Exception e) {
            logger.error("ERROR CRÍTICO en createTransaction", e);
            throw new RuntimeException("Error al crear la transacción", e);
        }
    }
} 
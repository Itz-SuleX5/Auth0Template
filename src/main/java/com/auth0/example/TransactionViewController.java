package com.auth0.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.io.IOException;

@Controller
public class TransactionViewController {
    private static final Logger logger = LoggerFactory.getLogger(TransactionViewController.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping("/new-transaction")
    public String showNewTransactionForm(Model model, @AuthenticationPrincipal OidcUser principal) {
        try {
            logger.info("=== INICIO showNewTransactionForm ===");
            logger.info("Creando nueva transacción");
            Transaction transaction = new Transaction();
            model.addAttribute("transaction", transaction);
            
            logger.info("Obteniendo categorías");
            List<Category> categories = categoryRepository.findAll().stream()
                .filter(category -> !"No especificado".equals(category.getName()))
                .collect(Collectors.toList());
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
    public String createTransaction(
            @ModelAttribute Transaction transaction,
            @RequestParam(value = "receipt", required = false) MultipartFile receipt,
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

            // Validar si es un gasto con método de pago AHORROS
            if (transaction.getType() == Transaction.TransactionType.GASTO && 
                transaction.getPaymentMethod() == Transaction.PaymentMethod.AHORROS) {
                
                // Obtener todas las transacciones del usuario
                List<Transaction> userTransactions = transactionRepository.findByUserMail(userEmail);
                
                // Calcular el total de ahorros disponible
                BigDecimal totalAhorros = BigDecimal.ZERO;
                for (Transaction t : userTransactions) {
                    // Sumar si es un ingreso en la categoría Ahorros
                    if (t.getCategory() != null && 
                        t.getCategory().getName().equals("Ahorros") && 
                        t.getType() == Transaction.TransactionType.INGRESO) {
                        totalAhorros = totalAhorros.add(t.getAmount());
                    }
                    // Sumar si es un ingreso con método de pago AHORROS
                    else if (t.getPaymentMethod() == Transaction.PaymentMethod.AHORROS && 
                            t.getType() == Transaction.TransactionType.INGRESO) {
                        totalAhorros = totalAhorros.add(t.getAmount());
                    }
                    // Restar si es un gasto con método de pago AHORROS
                    else if (t.getPaymentMethod() == Transaction.PaymentMethod.AHORROS && 
                            t.getType() == Transaction.TransactionType.GASTO) {
                        totalAhorros = totalAhorros.subtract(t.getAmount());
                    }
                }

                // Validar que el monto del gasto no exceda los ahorros disponibles
                if (transaction.getAmount().compareTo(totalAhorros) > 0) {
                    logger.warn("Intento de gastar más de lo disponible en ahorros. Disponible: {}, Intento de gasto: {}", 
                              totalAhorros, transaction.getAmount());
                    redirectAttributes.addFlashAttribute("error", 
                        "No puedes gastar más de lo que tienes en ahorros. Ahorros disponibles: " + totalAhorros);
                    return "redirect:/new-transaction";
                }
            }

            // Manejar la subida del archivo si existe
            if (receipt != null && !receipt.isEmpty()) {
                logger.info("Procesando archivo adjunto: {} ({} bytes)", 
                    receipt.getOriginalFilename(), receipt.getSize());
                try {
                    String filePath = fileStorageService.storeFile(receipt, userEmail);
                    transaction.setReceiptPath(filePath);
                    logger.info("Archivo guardado exitosamente en: {}", filePath);
                } catch (IOException e) {
                    logger.error("Error al guardar el archivo", e);
                    redirectAttributes.addFlashAttribute("error", "Error al guardar el archivo: " + e.getMessage());
                    return "redirect:/new-transaction";
                }
            } else {
                logger.info("No se adjuntó ningún archivo a la transacción");
            }

            logger.info("Guardando transacción");
            transactionRepository.save(transaction);
            logger.info("Transacción guardada exitosamente");
            
            logger.info("=== FIN createTransaction ===");
            redirectAttributes.addFlashAttribute("success", "Transacción creada exitosamente");
            return "redirect:/";
        } catch (Exception e) {
            logger.error("ERROR CRÍTICO en createTransaction", e);
            redirectAttributes.addFlashAttribute("error", "Error al crear la transacción: " + e.getMessage());
            return "redirect:/new-transaction";
        }
    }
} 
package com.auth0.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.io.IOException;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FileStorageService fileStorageService;

    public TransactionController() {
        logger.info("Inicializando TransactionController");
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions(@AuthenticationPrincipal OidcUser principal) {
        try {
            if (principal == null) {
                logger.warn("Intento de acceso sin autenticación");
                return ResponseEntity.ok(List.of());
            }

            logger.info("GET /api/transactions - Obteniendo transacciones del usuario");
            String userEmail = principal.getEmail();
            if (userEmail == null) {
                logger.error("Email del usuario no encontrado en los claims");
                return ResponseEntity.ok(List.of());
            }
            
            logger.info("Email del usuario: {}", userEmail);
            List<Transaction> transactions = transactionRepository.findAll().stream()
                .filter(t -> userEmail.equals(t.getUserMail()))
                .peek(t -> {
                    if (t.getCategory() != null && t.getCategory().getParentCategory() != null) {
                        Category parentCategory = t.getCategory().getParentCategory();
                        t.getCategory().setParentCategory(parentCategory);
                    }
                })
                .toList();
                
            logger.info("Transacciones encontradas: {}", transactions.size());
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            logger.error("Error al obtener transacciones: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        logger.info("GET /api/transactions/test - Endpoint de prueba");
        return ResponseEntity.ok("El controlador de transacciones está funcionando - " + System.currentTimeMillis());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createTransaction(
            @RequestPart("transaction") Transaction transaction,
            @RequestPart(value = "receipt", required = false) MultipartFile receipt,
            @AuthenticationPrincipal OidcUser principal) {
        try {
            logger.info("Recibida solicitud para crear transacción: {}", transaction);
            
            // Obtener el correo del usuario actual
            String userEmail = (String) principal.getClaims().get("email");
            transaction.setUserMail(userEmail);
            logger.info("Correo del usuario asignado: {}", userEmail);
            
            if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                logger.error("Monto inválido: {}", transaction.getAmount());
                return ResponseEntity.badRequest().body("El monto debe ser mayor que 0");
            }

            if (transaction.getCategory() == null || transaction.getCategory().getId() == null) {
                logger.error("Categoría inválida: {}", transaction.getCategory());
                return ResponseEntity.badRequest().body("La categoría es requerida");
            }

            Optional<Category> categoryOpt = categoryRepository.findById(transaction.getCategory().getId());
            if (categoryOpt.isEmpty()) {
                logger.error("Categoría no encontrada con ID: {}", transaction.getCategory().getId());
                return ResponseEntity.badRequest().body("Categoría no encontrada");
            }

            transaction.setCategory(categoryOpt.get());

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
                    return ResponseEntity.internalServerError()
                        .body("Error al guardar el archivo: " + e.getMessage());
                }
            } else {
                logger.info("No se adjuntó ningún archivo a la transacción");
            }

            Transaction savedTransaction = transactionRepository.save(transaction);
            logger.info("Transacción creada exitosamente: {}", savedTransaction);
            return ResponseEntity.ok(savedTransaction);
        } catch (Exception e) {
            logger.error("Error al crear transacción", e);
            return ResponseEntity.internalServerError().body("Error al crear la transacción: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTransactionById(@PathVariable Long id) {
        try {
            logger.info("Buscando transacción con ID: {}", id);
            Optional<Transaction> transaction = transactionRepository.findById(id);
            if (transaction.isPresent()) {
                logger.info("Transacción encontrada: {}", transaction.get());
                return ResponseEntity.ok(transaction.get());
            } else {
                logger.warn("Transacción no encontrada con ID: {}", id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error al buscar transacción con ID: " + id, e);
            return ResponseEntity.internalServerError().body("Error al buscar la transacción: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(@PathVariable Long id, @RequestBody Transaction transactionDetails) {
        try {
            logger.info("Actualizando transacción con ID: {}", id);
            Optional<Transaction> transactionOpt = transactionRepository.findById(id);
            if (transactionOpt.isEmpty()) {
                logger.warn("Transacción no encontrada con ID: {}", id);
                return ResponseEntity.notFound().build();
            }

            Transaction transaction = transactionOpt.get();
            transaction.setAmount(transactionDetails.getAmount());
            transaction.setDescription(transactionDetails.getDescription());
            transaction.setFecha(transactionDetails.getFecha());
            transaction.setType(transactionDetails.getType());
            
            if (transactionDetails.getCategory() != null && transactionDetails.getCategory().getId() != null) {
                Optional<Category> categoryOpt = categoryRepository.findById(transactionDetails.getCategory().getId());
                if (categoryOpt.isEmpty()) {
                    logger.error("Categoría no encontrada con ID: {}", transactionDetails.getCategory().getId());
                    return ResponseEntity.badRequest().body("Categoría no encontrada");
                }
                transaction.setCategory(categoryOpt.get());
            }

            Transaction updatedTransaction = transactionRepository.save(transaction);
            logger.info("Transacción actualizada exitosamente: {}", updatedTransaction);
            return ResponseEntity.ok(updatedTransaction);
        } catch (Exception e) {
            logger.error("Error al actualizar transacción con ID: " + id, e);
            return ResponseEntity.internalServerError().body("Error al actualizar la transacción: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
        try {
            logger.info("Eliminando transacción con ID: {}", id);
            if (!transactionRepository.existsById(id)) {
                logger.warn("Transacción no encontrada con ID: {}", id);
                return ResponseEntity.notFound().build();
            }
            transactionRepository.deleteById(id);
            logger.info("Transacción eliminada exitosamente");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("Error al eliminar transacción con ID: " + id, e);
            return ResponseEntity.internalServerError().body("Error al eliminar la transacción: " + e.getMessage());
        }
    }
} 
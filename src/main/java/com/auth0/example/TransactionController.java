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
import java.util.stream.Collectors;

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
    public ResponseEntity<?> getAllTransactions(@AuthenticationPrincipal OidcUser principal) {
        try {
            String userEmail = principal.getEmail();
            List<Transaction> transactions = transactionRepository.findByUserMail(userEmail);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            logger.error("Error al obtener las transacciones", e);
            return ResponseEntity.internalServerError().body("Error al obtener las transacciones: " + e.getMessage());
        }
    }

    @GetMapping("/balance")
    public ResponseEntity<?> getBalance(@AuthenticationPrincipal OidcUser principal) {
        try {
            String userEmail = principal.getEmail();
            List<Transaction> transactions = transactionRepository.findByUserMail(userEmail);
            
            BigDecimal total = BigDecimal.ZERO;
            for (Transaction transaction : transactions) {
                if (transaction.getType() == Transaction.TransactionType.INGRESO) {
                    total = total.add(transaction.getAmount());
                } else {
                    total = total.subtract(transaction.getAmount());
                }
            }
            
            return ResponseEntity.ok(total);
        } catch (Exception e) {
            logger.error("Error al calcular el balance", e);
            return ResponseEntity.internalServerError().body("Error al calcular el balance: " + e.getMessage());
        }
    }

    @GetMapping("/savings")
    public ResponseEntity<?> getSavings(@AuthenticationPrincipal OidcUser principal) {
        try {
            String userEmail = principal.getEmail();
            
            // Buscar la categoría Ahorros
            Category ahorrosCategory = categoryRepository.findByName("Ahorros");
            if (ahorrosCategory == null) {
                logger.info("No existe la categoría Ahorros");
                return ResponseEntity.ok(BigDecimal.ZERO);
            }

            // Obtener SOLO las transacciones de ahorros
            List<Transaction> ahorrosTransactions = transactionRepository.findByCategoryIdAndUserMail(ahorrosCategory.getId(), userEmail);
            logger.info("Transacciones de ahorros encontradas: {}", ahorrosTransactions.size());

            // Calcular el total SOLO de ahorros
            BigDecimal totalAhorros = BigDecimal.ZERO;
            for (Transaction transaction : ahorrosTransactions) {
                if (transaction.getType() == Transaction.TransactionType.INGRESO) {
                    totalAhorros = totalAhorros.add(transaction.getAmount());
                } else {
                    totalAhorros = totalAhorros.subtract(transaction.getAmount());
                }
            }

            logger.info("Total de ahorros calculado: {}", totalAhorros);
            return ResponseEntity.ok(totalAhorros);
        } catch (Exception e) {
            logger.error("Error al calcular el total de ahorros", e);
            return ResponseEntity.internalServerError().body("Error al calcular el total de ahorros: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody Transaction transaction, @AuthenticationPrincipal OidcUser principal) {
        try {
            logger.info("POST /api/transactions - Creando nueva transacción");
            
            // Si no se especificó categoría o es null, usar "No especificado"
            if (transaction.getCategory() == null || transaction.getCategory().getId() == null) {
                Category noEspecificado = categoryRepository.findByName("No especificado");
                if (noEspecificado == null) {
                    noEspecificado = new Category();
                    noEspecificado.setName("No especificado");
                    noEspecificado.setIcon("fas fa-question-circle");
                    noEspecificado = categoryRepository.save(noEspecificado);
                }
                transaction.setCategory(noEspecificado);
            }

            transaction.setUserMail(principal.getEmail());
            Transaction savedTransaction = transactionRepository.save(transaction);
            logger.info("Transacción creada exitosamente: {}", savedTransaction);
            return ResponseEntity.ok(savedTransaction);
        } catch (Exception e) {
            logger.error("Error al crear la transacción", e);
            return ResponseEntity.badRequest().body("Error al crear la transacción: " + e.getMessage());
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        logger.info("GET /api/transactions/test - Endpoint de prueba");
        return ResponseEntity.ok("El controlador de transacciones está funcionando - " + System.currentTimeMillis());
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
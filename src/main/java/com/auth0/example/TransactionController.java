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
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    public ResponseEntity<?> getTotalSavings(@AuthenticationPrincipal OidcUser principal) {
        try {
            logger.info("GET /api/transactions/savings - Calculando total de ahorros");
            String userEmail = principal.getEmail();
            
            // Buscar la categoría Ahorros
            Category ahorrosCategory = categoryRepository.findByName("Ahorros");
            if (ahorrosCategory == null) {
                logger.info("No existe la categoría Ahorros");
                return ResponseEntity.ok(BigDecimal.ZERO);
            }
            
            // Obtener todas las transacciones del usuario
            List<Transaction> userTransactions = transactionRepository.findByUserMail(userEmail);
            
            // Calcular el total de ahorros disponible
            BigDecimal totalAhorros = BigDecimal.ZERO;
            for (Transaction transaction : userTransactions) {
                // Sumar si es un ingreso en la categoría Ahorros
                if (transaction.getCategory() != null && 
                    transaction.getCategory().getName().equals("Ahorros") && 
                    transaction.getType() == Transaction.TransactionType.INGRESO) {
                    totalAhorros = totalAhorros.add(transaction.getAmount());
                }
                // Sumar si es un ingreso con método de pago AHORROS
                else if (transaction.getPaymentMethod() == Transaction.PaymentMethod.AHORROS && 
                        transaction.getType() == Transaction.TransactionType.INGRESO) {
                    totalAhorros = totalAhorros.add(transaction.getAmount());
                }
                // Restar si es un gasto con método de pago AHORROS
                else if (transaction.getPaymentMethod() == Transaction.PaymentMethod.AHORROS && 
                        transaction.getType() == Transaction.TransactionType.GASTO) {
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
    public ResponseEntity<?> createTransaction(@RequestParam("transaction") String transactionJson,
                                             @RequestParam(value = "receipt", required = false) MultipartFile receipt,
                                             @AuthenticationPrincipal OidcUser principal) {
        try {
            logger.info("POST /api/transactions - Creando nueva transacción");
            ObjectMapper mapper = new ObjectMapper();
            Transaction transaction = mapper.readValue(transactionJson, Transaction.class);

            // Validar si es un gasto con método de pago AHORROS
            if (transaction.getType() == Transaction.TransactionType.GASTO && 
                transaction.getPaymentMethod() == Transaction.PaymentMethod.AHORROS) {
                
                // Obtener todas las transacciones del usuario
                List<Transaction> userTransactions = transactionRepository.findByUserMail(principal.getEmail());
                
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
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("No puedes gastar más de lo que tienes en ahorros. Ahorros disponibles: " + totalAhorros);
                }
            }

            // Procesar el comprobante si existe
            if (receipt != null && !receipt.isEmpty()) {
                String fileName = receipt.getOriginalFilename();
                String receiptPath = fileStorageService.storeFile(receipt, fileName);
                transaction.setReceiptPath(receiptPath);
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
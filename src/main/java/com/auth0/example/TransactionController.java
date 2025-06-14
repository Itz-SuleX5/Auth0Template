package com.auth0.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    public TransactionController() {
        logger.info("Inicializando TransactionController");
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        logger.info("GET /api/transactions/test - Endpoint de prueba");
        return ResponseEntity.ok("El controlador de transacciones está funcionando - " + System.currentTimeMillis());
    }

    @GetMapping
    public ResponseEntity<?> getAllTransactions() {
        try {
            logger.info("GET /api/transactions - Obteniendo todas las transacciones");
            List<Transaction> transactions = transactionRepository.findAll();
            logger.info("Transacciones encontradas: {}", transactions.size());
            for (Transaction t : transactions) {
                logger.info("Transacción: id={}, descripción={}, monto={}, fecha={}, tipo={}, categoría={}",
                    t.getId(), t.getDescription(), t.getAmount(), t.getFecha(), t.getType(),
                    t.getCategory() != null ? t.getCategory().getId() : "null");
            }
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            logger.error("Error al obtener las transacciones", e);
            return ResponseEntity.internalServerError().body("Error al obtener las transacciones: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createTransaction(@RequestBody Transaction transaction) {
        try {
            logger.info("POST /api/transactions - Recibida petición para crear transacción: {}", transaction);
            
            // Validar que la transacción no sea nula
            if (transaction == null) {
                logger.error("Error: La transacción no puede ser nula");
                return ResponseEntity.badRequest().body("La transacción no puede ser nula");
            }

            // Validar campos requeridos
            if (transaction.getDescription() == null || transaction.getDescription().trim().isEmpty()) {
                logger.error("Error: La descripción es requerida");
                return ResponseEntity.badRequest().body("La descripción es requerida");
            }

            if (transaction.getAmount() == null) {
                logger.error("Error: El monto es requerido");
                return ResponseEntity.badRequest().body("El monto es requerido");
            }

            if (transaction.getFecha() == null) {
                logger.error("Error: La fecha es requerida");
                return ResponseEntity.badRequest().body("La fecha es requerida");
            }

            if (transaction.getType() == null) {
                logger.error("Error: El tipo de transacción es requerido");
                return ResponseEntity.badRequest().body("El tipo de transacción es requerido");
            }

            // Validar y obtener la categoría
            if (transaction.getCategory() == null || transaction.getCategory().getId() == null) {
                logger.error("Error: La categoría es requerida");
                return ResponseEntity.badRequest().body("La categoría es requerida");
            }

            Category category = categoryRepository.findById(transaction.getCategory().getId())
                .orElse(null);

            if (category == null) {
                logger.error("Error: La categoría con ID {} no existe", transaction.getCategory().getId());
                return ResponseEntity.badRequest().body("La categoría especificada no existe");
            }

            // Asignar la categoría encontrada
            transaction.setCategory(category);

            // Guardar la transacción
            Transaction savedTransaction = transactionRepository.save(transaction);
            logger.info("Transacción creada exitosamente: id={}, descripción={}, monto={}, fecha={}, tipo={}, categoría={}",
                savedTransaction.getId(), savedTransaction.getDescription(), savedTransaction.getAmount(),
                savedTransaction.getFecha(), savedTransaction.getType(),
                savedTransaction.getCategory() != null ? savedTransaction.getCategory().getId() : "null");
            
            return ResponseEntity.ok(savedTransaction);
        } catch (Exception e) {
            logger.error("Error al crear la transacción", e);
            return ResponseEntity.badRequest().body("Error al crear la transacción: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable Long id) {
        logger.info("GET /api/transactions/{} - Obteniendo transacción por ID", id);
        return transactionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(@PathVariable Long id, @RequestBody Transaction transactionDetails) {
        logger.info("PUT /api/transactions/{} - Actualizando transacción", id);
        return transactionRepository.findById(id)
                .map(existingTransaction -> {
                    existingTransaction.setDescription(transactionDetails.getDescription());
                    existingTransaction.setAmount(transactionDetails.getAmount());
                    existingTransaction.setFecha(transactionDetails.getFecha());
                    existingTransaction.setCategory(transactionDetails.getCategory());
                    existingTransaction.setType(transactionDetails.getType());
                    return ResponseEntity.ok(transactionRepository.save(existingTransaction));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
        logger.info("DELETE /api/transactions/{} - Eliminando transacción", id);
        return transactionRepository.findById(id)
                .map(transaction -> {
                    transactionRepository.delete(transaction);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
} 
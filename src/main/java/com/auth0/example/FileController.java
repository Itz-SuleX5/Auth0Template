package com.auth0.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

@RestController
@RequestMapping("/api/receipts")
public class FileController {
    
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");

    @GetMapping("/{userId}/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String userId, @PathVariable String filename) {
        try {
            logger.info("Intentando servir archivo: {}/{}", userId, filename);
            Path file = uploadDir.resolve(userId).resolve(filename);
            logger.info("Ruta completa del archivo: {}", file.toAbsolutePath());
            
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                logger.info("Archivo encontrado y legible");
                
                // Determinar el tipo de contenido basado en la extensión del archivo
                String contentType = determineContentType(filename);
                logger.info("Tipo de contenido detectado: {}", contentType);
                
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
            } else {
                logger.error("Archivo no encontrado o no legible: {}", file.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("Error al servir el archivo", e);
            return ResponseEntity.notFound().build();
        }
    }

    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            default:
                return "application/octet-stream";
        }
    }
} 
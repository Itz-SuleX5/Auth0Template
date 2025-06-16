package com.auth0.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
    private final Path uploadDir;

    public FileStorageService() {
        // Usar una ruta absoluta para el directorio de uploads
        this.uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");
        try {
            if (!Files.exists(uploadDir)) {
                logger.info("Creando directorio de uploads en: {}", uploadDir.toAbsolutePath());
                Files.createDirectories(uploadDir);
            }
            logger.info("Directorio de uploads inicializado en: {}", uploadDir.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Error al crear el directorio de uploads", e);
            throw new RuntimeException("No se pudo crear el directorio de uploads", e);
        }
    }

    public String storeFile(MultipartFile file, String userEmail) throws IOException {
        logger.info("Iniciando almacenamiento de archivo para usuario: {}", userEmail);
        
        // Crear directorio específico para el usuario si no existe
        Path userDir = uploadDir.resolve(userEmail);
        if (!Files.exists(userDir)) {
            logger.info("Creando directorio para usuario en: {}", userDir.toAbsolutePath());
            Files.createDirectories(userDir);
        }

        // Generar nombre único para el archivo
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String newFilename = UUID.randomUUID().toString() + extension;
        logger.info("Nombre de archivo generado: {}", newFilename);

        // Guardar el archivo
        Path filePath = userDir.resolve(newFilename);
        logger.info("Guardando archivo en: {}", filePath.toAbsolutePath());
        
        // Asegurarse de que el directorio existe antes de guardar
        Files.createDirectories(filePath.getParent());
        
        // Guardar el archivo
        Files.copy(file.getInputStream(), filePath);
        
        // Verificar que el archivo se guardó correctamente
        if (!Files.exists(filePath)) {
            throw new IOException("No se pudo guardar el archivo en: " + filePath);
        }
        
        logger.info("Archivo guardado exitosamente. Tamaño: {} bytes", Files.size(filePath));

        // Retornar la ruta relativa del archivo
        String relativePath = userEmail + "/" + newFilename;
        logger.info("Ruta relativa del archivo: {}", relativePath);
        return relativePath;
    }
} 
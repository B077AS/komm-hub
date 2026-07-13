package com.kommhub.controller;

import com.kommhub.model.dto.response.ClientVersionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@RestController
@RequestMapping("/api/client")
public class ClientUpdateController {

    @Value("${kommhub.client.jar.path}")
    private String jarPath;

    @Value("${kommhub.client.properties-entry}")
    private String propertiesEntry;

    @GetMapping("/latest")
    public ResponseEntity<ClientVersionResponse> getLatest() {
        Path jar = Paths.get(jarPath);
        if (!Files.exists(jar)) {
            log.error("Client JAR not found at: {}", jar.toAbsolutePath());
            return ResponseEntity.notFound().build();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jar.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(propertiesEntry)) {
                    Properties props = new Properties();
                    props.load(zis);

                    String version = props.getProperty("client.version");

                    String downloadUrl = ServletUriComponentsBuilder.fromCurrentRequest()
                            .replacePath("/api/client/download")
                            .replaceQuery(null)
                            .toUriString();

                    return ResponseEntity.ok(ClientVersionResponse.builder()
                            .version(version)
                            .downloadUrl(downloadUrl)
                            .build());
                }
                zis.closeEntry();
            }
            log.error("Properties entry '{}' not found in JAR: {}", propertiesEntry, jarPath);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Failed to read client version from JAR: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download() {
        try {
            Path jar = Paths.get(jarPath);
            if (!Files.exists(jar)) {
                log.error("Client JAR not found at: {}", jar.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(jar);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=komm-app.jar")
                    .contentLength(jar.toFile().length())
                    .body(resource);
        } catch (Exception e) {
            log.error("Failed to serve client JAR: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
